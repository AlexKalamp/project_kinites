package gr.uni.kinites.indoorsignalmap.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Δημιουργεί αρχιτεκτονικές κατόψεις (Bitmaps) για τους 3 ορόφους του κτιρίου
 * (Ισόγειο, 1ος Όροφος, 2ος Όροφος), ώστε το σύστημα Πολλαπλών Ορόφων (Bonus)
 * να διαθέτει ρεαλιστικές, ευδιάκριτες κατόψεις από την πρώτη εκτέλεση.
 * Ο χρήστης μπορεί επίσης να επιλέξει οποιαδήποτε εικόνα κάτοψης από τη συλλογή της συσκευής.
 */
object DefaultFloorPlanGenerator {

    private const val WIDTH = 1200
    private const val HEIGHT = 900

    fun createFloorPlanBitmap(floorId: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Φόντο χαρτιού αρχιτεκτονικού σχεδίου
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        // Σχεδίαση διακριτικού κανάβου (grid)
        val step = 60
        for (x in 0..WIDTH step step) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), HEIGHT.toFloat(), gridPaint)
        }
        for (y in 0..HEIGHT step step) {
            canvas.drawLine(0f, y.toFloat(), WIDTH.toFloat(), y.toFloat(), gridPaint)
        }

        val outerWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            strokeWidth = 12f
            style = Paint.Style.STROKE
        }
        val innerWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            strokeWidth = 7f
            style = Paint.Style.STROKE
        }
        val roomFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            style = Paint.Style.FILL
        }
        val corridorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val roomLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        val stairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            strokeWidth = 10f
            style = Paint.Style.STROKE
        }
        val doorArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }

        val outerRect = RectF(60f, 80f, WIDTH - 60f, HEIGHT - 60f)
        canvas.drawRect(outerRect, roomFillPaint)

        when (floorId) {
            0 -> drawGroundFloor(
                canvas, outerRect, corridorFillPaint, innerWallPaint,
                roomLabelPaint, subLabelPaint, stairPaint, doorPaint, doorArcPaint
            )
            1 -> drawFirstFloor(
                canvas, outerRect, corridorFillPaint, innerWallPaint,
                roomLabelPaint, subLabelPaint, stairPaint, doorPaint, doorArcPaint
            )
            else -> drawSecondFloor(
                canvas, outerRect, corridorFillPaint, innerWallPaint,
                roomLabelPaint, subLabelPaint, stairPaint, doorPaint, doorArcPaint
            )
        }

        // Εξωτερικό περίγραμμα κτιρίου
        canvas.drawRect(outerRect, outerWallPaint)

        // Επικεφαλίδα σχεδίου
        val floorTitle = when (floorId) {
            0 -> "ΚΑΤΟΨΗ ΙΣΟΓΕΙΟΥ (GROUND FLOOR - 0)"
            1 -> "ΚΑΤΟΨΗ 1ου ΟΡΟΦΟΥ (FIRST FLOOR - 1)"
            else -> "ΚΑΤΟΨΗ 2ου ΟΡΟΦΟΥ (SECOND FLOOR - 2)"
        }
        canvas.drawText(floorTitle, 60f, 52f, titlePaint)

        // Ένδειξη Βορρά
        val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("N ▲  Κλίμακα 1:100", WIDTH - 60f, 52f, northPaint)

        return bitmap
    }

    private fun drawGroundFloor(
        canvas: Canvas,
        outer: RectF,
        corridorFill: Paint,
        wall: Paint,
        label: Paint,
        subLabel: Paint,
        stair: Paint,
        doorClear: Paint,
        doorArc: Paint
    ) {
        // Κεντρικός Διάδρομος / Υποδοχή
        val corridor = RectF(outer.left, 390f, outer.right, 540f)
        canvas.drawRect(corridor, corridorFill)

        // Πάνω Δωμάτια: Αμφιθέατρο Α (αριστερά), Γραμματεία (κέντρο), Κλιμακοστάσιο/Ασανσέρ (δεξιά)
        canvas.drawLine(outer.left, 390f, outer.right, 390f, wall)
        canvas.drawLine(560f, outer.top, 560f, 390f, wall)
        canvas.drawLine(880f, outer.top, 880f, 390f, wall)

        // Κάτω Δωμάτια: Εργαστήριο Δικτύων (αριστερά), Καφετέρια / Lobby (κέντρο), Server Room (δεξιά)
        canvas.drawLine(outer.left, 540f, outer.right, 540f, wall)
        canvas.drawLine(480f, 540f, 480f, outer.bottom, wall)
        canvas.drawLine(860f, 540f, 860f, outer.bottom, wall)

        // Σκάλες
        drawStairs(canvas, RectF(910f, 110f, 1110f, 240f), stair)

        // Ανοίγματα Πορτών
        drawDoorHorizontal(canvas, 280f, 390f, 70f, true, doorClear, doorArc)
        drawDoorHorizontal(canvas, 700f, 390f, 65f, true, doorClear, doorArc)
        drawDoorHorizontal(canvas, 250f, 540f, 70f, false, doorClear, doorArc)
        drawDoorHorizontal(canvas, 650f, 540f, 70f, false, doorClear, doorArc)
        drawDoorHorizontal(canvas, 980f, 540f, 65f, false, doorClear, doorArc)

        // Ετικέτες Χώρων
        drawRoomText(canvas, "ΑΜΦΙΘΕΑΤΡΟ Α", "Ισχυρό 5G/4G κοντά στα παράθυρα", 310f, 230f, label, subLabel)
        drawRoomText(canvas, "ΓΡΑΜΜΑΤΕΙΑ", "Γραφείο Υποδοχής", 720f, 230f, label, subLabel)
        drawRoomText(canvas, "ΚΛΙΜΑΚΟΣΤΑΣΙΟ", "Ανελκυστήρας & Σκάλες", 1010f, 310f, label, subLabel)
        drawRoomText(canvas, "ΚΕΝΤΡΙΚΟΣ ΔΙΑΔΡΟΜΟΣ ΙΣΟΓΕΙΟΥ", "Κύρια Είσοδος Κτιρίου ◀", 600f, 470f, label, subLabel)
        drawRoomText(canvas, "ΕΡΓΑΣΤΗΡΙΟ ΚΙΝΗΤΩΝ", "Πάγκοι Δοκιμών", 270f, 690f, label, subLabel)
        drawRoomText(canvas, "ΑΙΘΟΥΣΑ ΑΝΑΜΟΝΗΣ", "Χώρος Φοιτητών", 670f, 690f, label, subLabel)
        drawRoomText(canvas, "SERVER ROOM", "Χαμηλό Σήμα (Θωράκιση)", 1000f, 690f, label, subLabel)
    }

    private fun drawFirstFloor(
        canvas: Canvas,
        outer: RectF,
        corridorFill: Paint,
        wall: Paint,
        label: Paint,
        subLabel: Paint,
        stair: Paint,
        doorClear: Paint,
        doorArc: Paint
    ) {
        val corridor = RectF(outer.left, 400f, outer.right, 530f)
        canvas.drawRect(corridor, corridorFill)

        // Πάνω σειρά: Αίθουσα 101, Αίθουσα 102, Αίθουσα 103, Κλιμακοστάσιο
        canvas.drawLine(outer.left, 400f, outer.right, 400f, wall)
        canvas.drawLine(360f, outer.top, 360f, 400f, wall)
        canvas.drawLine(640f, outer.top, 640f, 400f, wall)
        canvas.drawLine(900f, outer.top, 900f, 400f, wall)

        // Κάτω σειρά: Εργαστήριο Τηλεπικοινωνιών, Γραφεία Διδασκόντων, WC / Αποθήκη
        canvas.drawLine(outer.left, 530f, outer.right, 530f, wall)
        canvas.drawLine(540f, 530f, 540f, outer.bottom, wall)
        canvas.drawLine(880f, 530f, 880f, outer.bottom, wall)

        drawStairs(canvas, RectF(920f, 110f, 1110f, 240f), stair)

        drawDoorHorizontal(canvas, 190f, 400f, 65f, true, doorClear, doorArc)
        drawDoorHorizontal(canvas, 480f, 400f, 65f, true, doorClear, doorArc)
        drawDoorHorizontal(canvas, 750f, 400f, 65f, true, doorClear, doorArc)
        drawDoorHorizontal(canvas, 280f, 530f, 70f, false, doorClear, doorArc)
        drawDoorHorizontal(canvas, 700f, 530f, 65f, false, doorClear, doorArc)
        drawDoorHorizontal(canvas, 980f, 530f, 60f, false, doorClear, doorArc)

        drawRoomText(canvas, "ΑΙΘΟΥΣΑ 101", "Διδασκαλία", 210f, 240f, label, subLabel)
        drawRoomText(canvas, "ΑΙΘΟΥΣΑ 102", "Διδασκαλία", 500f, 240f, label, subLabel)
        drawRoomText(canvas, "ΑΙΘΟΥΣΑ 103", "Σεμινάρια", 770f, 240f, label, subLabel)
        drawRoomText(canvas, "ΚΛΙΜΑΚΟΣΤΑΣΙΟ", "1ος Όροφος", 1020f, 320f, label, subLabel)
        drawRoomText(canvas, "ΔΙΑΔΡΟΜΟΣ 1ου ΟΡΟΦΟΥ", "Εσωτερικός Διάδρομος", 600f, 470f, label, subLabel)
        drawRoomText(canvas, "ΕΡΓ. ΤΗΛΕΠΙΚΟΙΝΩΝΙΩΝ", "Μετρήσεις RF & Κεραίες", 300f, 690f, label, subLabel)
        drawRoomText(canvas, "ΓΡΑΦΕΙΑ ΚΑΘΗΓΗΤΩΝ", "Πτέρυγα Α", 710f, 690f, label, subLabel)
        drawRoomText(canvas, "WC / Η/Μ", "Εσωτερικός Χώρος", 1010f, 690f, label, subLabel)
    }

    private fun drawSecondFloor(
        canvas: Canvas,
        outer: RectF,
        corridorFill: Paint,
        wall: Paint,
        label: Paint,
        subLabel: Paint,
        stair: Paint,
        doorClear: Paint,
        doorArc: Paint
    ) {
        val corridor = RectF(outer.left, 390f, 900f, 530f)
        canvas.drawRect(corridor, corridorFill)

        // Πάνω σειρά: Βιβλιοθήκη & Αναγνωστήριο (μεγάλος χώρος), Κλιμακοστάσιο
        canvas.drawLine(outer.left, 390f, 900f, 390f, wall)
        canvas.drawLine(620f, outer.top, 620f, 390f, wall)
        canvas.drawLine(900f, outer.top, 900f, outer.bottom, wall)

        // Κάτω σειρά: Αίθουσα Συνεδριάσεων, Ερευνητικό Εργαστήριο 5G
        canvas.drawLine(outer.left, 530f, 900f, 530f, wall)
        canvas.drawLine(450f, 530f, 450f, outer.bottom, wall)

        drawStairs(canvas, RectF(930f, 110f, 1110f, 240f), stair)

        drawDoorHorizontal(canvas, 320f, 390f, 75f, true, doorClear, doorArc)
        drawDoorHorizontal(canvas, 740f, 390f, 65f, true, doorClear, doorArc)
        drawDoorHorizontal(canvas, 230f, 530f, 70f, false, doorClear, doorArc)
        drawDoorHorizontal(canvas, 660f, 530f, 70f, false, doorClear, doorArc)

        drawRoomText(canvas, "ΚΕΝΤΡΙΚΗ ΒΙΒΛΙΟΘΗΚΗ", "Αναγνωστήριο 2ου Ορόφου", 340f, 230f, label, subLabel)
        drawRoomText(canvas, "ΑΙΘΟΥΣΑ Η/Υ", "Νησίδα Υπολογιστών", 760f, 230f, label, subLabel)
        drawRoomText(canvas, "ΔΙΑΔΡΟΜΟΣ 2ου ΟΡΟΦΟΥ", "Πρόσβαση Βιβλιοθήκης", 480f, 465f, label, subLabel)
        drawRoomText(canvas, "ΑΙΘΟΥΣΑ ΣΥΝΕΔΡΙΑΣΕΩΝ", "Meeting Room", 255f, 690f, label, subLabel)
        drawRoomText(canvas, "ΕΡΕΥΝΗΤΙΚΟ ΕΡΓ. 5G", "Κεραίες & Small Cells", 675f, 690f, label, subLabel)
        drawRoomText(canvas, "ΚΛΙΜΑΚΟΣΤΑΣΙΟ", "& Αίθριο Ορόφου", 1020f, 530f, label, subLabel)
    }

    private fun drawStairs(canvas: Canvas, rect: RectF, paint: Paint) {
        canvas.drawRect(rect, paint)
        val steps = 8
        val stepW = rect.width() / steps
        for (i in 1 until steps) {
            val x = rect.left + i * stepW
            canvas.drawLine(x, rect.top, x, rect.bottom, paint)
        }
        canvas.drawLine(rect.left + 15f, rect.centerY(), rect.right - 15f, rect.centerY(), paint)
    }

    private fun drawDoorHorizontal(
        canvas: Canvas,
        startX: Float,
        wallY: Float,
        width: Float,
        opensUp: Boolean,
        clearPaint: Paint,
        arcPaint: Paint
    ) {
        canvas.drawLine(startX, wallY, startX + width, wallY, clearPaint)
        val arcRect = if (opensUp) {
            RectF(startX, wallY - width, startX + width * 2, wallY + width)
        } else {
            RectF(startX, wallY - width, startX + width * 2, wallY + width)
        }
        val startAngle = if (opensUp) 180f else 90f
        canvas.drawArc(arcRect, startAngle, 90f, true, arcPaint)
    }

    private fun drawRoomText(
        canvas: Canvas,
        title: String,
        subtitle: String,
        cx: Float,
        cy: Float,
        titlePaint: Paint,
        subPaint: Paint
    ) {
        canvas.drawText(title, cx, cy, titlePaint)
        canvas.drawText(subtitle, cx, cy + 28f, subPaint)
    }
}
