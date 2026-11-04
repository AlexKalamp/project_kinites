package gr.uni.kinites.indoorsignalmap.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import gr.uni.kinites.indoorsignalmap.data.SignalPointEntity
import gr.uni.kinites.indoorsignalmap.util.SignalColorMapper
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View που κληρονομεί από την κλάση View και υπερβαίνει τη μέθοδο onDraw(Canvas).
 * Σχεδιάζει:
 *  1. Το Bitmap της κάτοψης του τρέχοντος ορόφου.
 *  2. Τους ημιδιαφανείς κύκλους του Heatmap με RadialGradient (fade-out) και Linear Interpolation
 *     χρώματος βάσει της τιμής dBm (ή απλούς μονόχρωμους markers αν επιλεγεί για σύγκριση).
 *  3. Υποστηρίζει Pinch-to-Zoom και Panning (Drag) με δύο ή ένα δάχτυλο (Bonus).
 */
class HeatmapFloorPlanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var floorBitmap: Bitmap? = null
    private var signalPoints: List<SignalPointEntity> = emptyList()

    /**
     * Όταν είναι true (default), σχεδιάζει RadialGradient heatmap με γραμμική παρεμβολή χρώματος.
     * Όταν είναι false, σχεδιάζει απλούς μονόχρωμους markers (Βασικό Επίπεδο) για επίδειξη.
     */
    var useRadialHeatmap: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Ακτίνα του κάθε κύκλου heatmap πάνω στις συντεταγμένες της κάτοψης.
     */
    var heatmapRadiusPx: Float = 95f
        set(value) {
            field = value.coerceIn(30f, 260f)
            invalidate()
        }

    /**
     * Callback όταν ο χρήστης κάνει Tap πάνω στην κάτοψη.
     * Επιστρέφει τις κανονικοποιημένες συντεταγμένες (normalizedX, normalizedY) στο διάστημα [0..1].
     */
    var onFloorTapListener: ((normalizedX: Float, normalizedY: Float) -> Unit)? = null

    /**
     * Callback όταν αλλάζει το επίπεδο Zoom.
     */
    var onZoomChangedListener: ((scaleFactor: Float) -> Unit)? = null

    // Πίνακας μετασχηματισμού για Pinch-to-Zoom & Panning
    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val matrixValues = FloatArray(9)

    private var baseScale = 1f
    private var minScale = 0.8f
    private var maxScale = 5.0f
    private var isInitializedMatrix = false

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 15, 23, 42)
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#CBD5E1")
        strokeWidth = 3f
    }

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentScale = getCurrentScale()
                var scaleFactor = detector.scaleFactor
                val targetScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
                scaleFactor = targetScale / currentScale

                transformMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                constrainMatrixBounds()
                onZoomChangedListener?.invoke(getCurrentScale() / baseScale)
                invalidate()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                handleTapAtScreenPoint(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap για γρήγορο Zoom-In ή επαναφορά (Reset Zoom)
                val currentZoom = getCurrentScale() / baseScale
                if (currentZoom > 1.6f) {
                    resetZoomAndPan()
                } else {
                    transformMatrix.postScale(1.8f, 1.8f, e.x, e.y)
                    constrainMatrixBounds()
                    onZoomChangedListener?.invoke(getCurrentScale() / baseScale)
                    invalidate()
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (!scaleGestureDetector.isInProgress) {
                    transformMatrix.postTranslate(-distanceX, -distanceY)
                    constrainMatrixBounds()
                    invalidate()
                }
                return true
            }
        }
    )

    fun setFloorPlanBitmap(bitmap: Bitmap) {
        floorBitmap = bitmap
        isInitializedMatrix = false
        fitBitmapToView()
        invalidate()
    }

    fun setPoints(points: List<SignalPointEntity>) {
        signalPoints = points
        invalidate()
    }

    fun resetZoomAndPan() {
        isInitializedMatrix = false
        fitBitmapToView()
        onZoomChangedListener?.invoke(1.0f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitBitmapToView()
    }

    private fun fitBitmapToView() {
        val bmp = floorBitmap ?: return
        if (width <= 0 || height <= 0) return

        val pad = 20f
        val availW = (width - pad * 2).coerceAtLeast(1f)
        val availH = (height - pad * 2).coerceAtLeast(1f)

        val scaleX = availW / bmp.width.toFloat()
        val scaleY = availH / bmp.height.toFloat()
        baseScale = min(scaleX, scaleY)
        minScale = baseScale * 0.75f
        maxScale = baseScale * 5.0f

        val dx = (width - bmp.width * baseScale) / 2f
        val dy = (height - bmp.height * baseScale) / 2f

        transformMatrix.reset()
        transformMatrix.postScale(baseScale, baseScale)
        transformMatrix.postTranslate(dx, dy)
        isInitializedMatrix = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp = floorBitmap ?: return
        if (!isInitializedMatrix) {
            fitBitmapToView()
        }

        canvas.save()
        canvas.concat(transformMatrix)

        // 1. Σχεδίαση του Bitmap της κάτοψης
        canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), borderPaint)

        // 2. Σχεδίαση των σημείων σήματος (Heatmap με RadialGradient ή απλοί Markers)
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        for (pt in signalPoints) {
            val cx = pt.normalizedX * bmpW
            val cy = pt.normalizedY * bmpH

            // Υπολογισμός χρώματος με Linear Interpolation και Alpha ~140 (55%)
            val centerColor = SignalColorMapper.getInterpolatedColor(
                dbm = pt.dbm,
                alpha = SignalColorMapper.DEFAULT_HEATMAP_ALPHA
            )
            val midColor = SignalColorMapper.getInterpolatedColor(
                dbm = pt.dbm,
                alpha = (SignalColorMapper.DEFAULT_HEATMAP_ALPHA * 0.55f).toInt()
            )
            val edgeColor = Color.argb(
                0,
                Color.red(centerColor),
                Color.green(centerColor),
                Color.blue(centerColor)
            )

            if (useRadialHeatmap) {
                val radius = heatmapRadiusPx
                val radialGradient = RadialGradient(
                    cx,
                    cy,
                    radius,
                    intArrayOf(centerColor, midColor, edgeColor),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                gradientPaint.shader = radialGradient
                canvas.drawCircle(cx, cy, radius, gradientPaint)
            }

            // Κεντρική κουκκίδα (Marker) και ένδειξη dBm
            val solidColor = SignalColorMapper.getInterpolatedColor(pt.dbm, 240)
            centerDotPaint.color = solidColor
            val markerRadius = if (useRadialHeatmap) 11f else 22f
            canvas.drawCircle(cx, cy, markerRadius, centerDotPaint)
            canvas.drawCircle(cx, cy, markerRadius, centerStrokePaint)

            // Ετικέτα τιμής dBm πάνω από το σημείο
            val labelText = "${pt.dbm} dBm"
            val textWidth = labelTextPaint.measureText(labelText)
            val pillRect = RectF(
                cx - textWidth / 2f - 10f,
                cy - markerRadius - 36f,
                cx + textWidth / 2f + 10f,
                cy - markerRadius - 8f
            )
            canvas.drawRoundRect(pillRect, 8f, 8f, labelBgPaint)
            canvas.drawText(labelText, cx, cy - markerRadius - 15f, labelTextPaint)
        }

        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }

    private fun handleTapAtScreenPoint(screenX: Float, screenY: Float) {
        val bmp = floorBitmap ?: return
        if (!transformMatrix.invert(inverseMatrix)) return

        val pts = floatArrayOf(screenX, screenY)
        inverseMatrix.mapPoints(pts)

        val bitmapX = pts[0]
        val bitmapY = pts[1]

        // Έλεγχος ότι το Tap έγινε εντός των ορίων της κάτοψης
        if (bitmapX in 0f..bmp.width.toFloat() && bitmapY in 0f..bmp.height.toFloat()) {
            val normX = (bitmapX / bmp.width.toFloat()).coerceIn(0f, 1f)
            val normY = (bitmapY / bmp.height.toFloat()).coerceIn(0f, 1f)
            onFloorTapListener?.invoke(normX, normY)
        }
    }

    private fun getCurrentScale(): Float {
        transformMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun constrainMatrixBounds() {
        val bmp = floorBitmap ?: return
        transformMatrix.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        val scaledW = bmp.width * scale
        val scaledH = bmp.height * scale

        val margin = 80f
        val minX = min(margin, width - scaledW - margin)
        val maxX = max(width - margin, scaledW + margin) - scaledW
        val minY = min(margin, height - scaledH - margin)
        val maxY = max(height - margin, scaledH + margin) - scaledH

        val clampedX = transX.coerceIn(minX, maxX)
        val clampedY = transY.coerceIn(minY, maxY)

        if (clampedX != transX || clampedY != transY) {
            transformMatrix.postTranslate(clampedX - transX, clampedY - transY)
        }
    }
}
