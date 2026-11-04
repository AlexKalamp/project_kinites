package gr.uni.kinites.indoorsignalmap.util

import android.graphics.Color

/**
 * Χρωματική κλίμακα και Γραμμική Παρεμβολή (Linear Interpolation) βάσει των προδιαγραφών:
 *
 *  -60 dBm έως -40 dBm  -> Εξαιρετικό (Excellent)   -> Πράσινο    #00FF00
 *  -80 dBm έως -61 dBm  -> Πολύ Καλό (Good)         -> Λαχανί     #99FF00
 *  -90 dBm έως -81 dBm  -> Μέτριο (Fair)            -> Κίτρινο    #FFFF00
 * -100 dBm έως -91 dBm  -> Κακό (Poor)              -> Πορτοκαλί  #FF9900
 * -115 dBm έως -101 dBm -> Πολύ Κακό / Οριακό       -> Κόκκινο    #FF0000
 * < -115 dBm            -> Καμία Σύνδεση (No Signal)-> Γκρι       #444444
 */
object SignalColorMapper {

    const val COLOR_EXCELLENT = 0xFF00FF00.toInt() // #00FF00
    const val COLOR_GOOD      = 0xFF99FF00.toInt() // #99FF00
    const val COLOR_FAIR      = 0xFFFFFF00.toInt() // #FFFF00
    const val COLOR_POOR      = 0xFFFF9900.toInt() // #FF9900
    const val COLOR_VERY_POOR = 0xFFFF0000.toInt() // #FF0000
    const val COLOR_NO_SIGNAL = 0xFF444444.toInt() // #444444

    /**
     * Default alpha value (~55% = 140 / 255) as recommended in the specification.
     */
    const val DEFAULT_HEATMAP_ALPHA = 140

    /**
     * Επιστρέφει την ποιοτική περιγραφή του σήματος βάσει του πίνακα προδιαγραφών.
     */
    fun getQualityLabel(dbm: Int): String {
        return when {
            dbm >= -60 -> "Εξαιρετικό (Excellent)"
            dbm in -80..-61 -> "Πολύ Καλό (Good)"
            dbm in -90..-81 -> "Μέτριο (Fair)"
            dbm in -100..-91 -> "Κακό (Poor)"
            dbm in -115..-101 -> "Πολύ Κακό / Οριακό"
            else -> "Καμία Σύνδεση (No Signal)"
        }
    }

    /**
     * Επιστρέφει το διακριτό χρώμα (χωρίς παρεμβολή) βάσει του πίνακα.
     */
    fun getDiscreteColor(dbm: Int, alpha: Int = 255): Int {
        val base = when {
            dbm >= -60 -> COLOR_EXCELLENT
            dbm in -80..-61 -> COLOR_GOOD
            dbm in -90..-81 -> COLOR_FAIR
            dbm in -100..-91 -> COLOR_POOR
            dbm in -115..-101 -> COLOR_VERY_POOR
            else -> COLOR_NO_SIGNAL
        }
        return Color.argb(alpha.coerceIn(0, 255), Color.red(base), Color.green(base), Color.blue(base))
    }

    /**
     * Επιστρέφει το χρώμα με Γραμμική Παρεμβολή (Linear Interpolation) ανάμεσα στις ζώνες dBm,
     * ώστε οι μεταβάσεις στον θερμικό χάρτη να είναι απόλυτα ομαλές.
     */
    fun getInterpolatedColor(dbm: Int, alpha: Int = DEFAULT_HEATMAP_ALPHA): Int {
        val clampedAlpha = alpha.coerceIn(0, 255)

        if (dbm < -115) {
            // < -115 dBm: Ομαλή μετάβαση από Κόκκινο (#FF0000) σε Γκρι (#444444) έως τα -120 dBm
            val t = ((-115f - dbm.coerceAtLeast(-120)) / 5f).coerceIn(0f, 1f)
            return lerpColor(COLOR_VERY_POOR, COLOR_NO_SIGNAL, t, clampedAlpha)
        }

        // Κόμβοι παρεμβολής (Anchor points) βάσει του πίνακα προδιαγραφών:
        // >= -50 dBm : Καθαρό Πράσινο (#00FF00)
        //    -60 dBm : Πράσινο (#00FF00)
        //    -70 dBm : Λαχανί (#99FF00)
        //    -85 dBm : Κίτρινο (#FFFF00)
        //    -95 dBm : Πορτοκαλί (#FF9900)
        //   -108 dBm : Κόκκινο (#FF0000)
        //   -115 dBm : Κόκκινο (#FF0000)
        return when {
            dbm >= -60 -> applyAlpha(COLOR_EXCELLENT, clampedAlpha)
            dbm >= -70 -> {
                val t = (-60f - dbm) / 10f // -60 -> 0.0, -70 -> 1.0
                lerpColor(COLOR_EXCELLENT, COLOR_GOOD, t, clampedAlpha)
            }
            dbm >= -85 -> {
                val t = (-70f - dbm) / 15f // -70 -> 0.0, -85 -> 1.0
                lerpColor(COLOR_GOOD, COLOR_FAIR, t, clampedAlpha)
            }
            dbm >= -95 -> {
                val t = (-85f - dbm) / 10f // -85 -> 0.0, -95 -> 1.0
                lerpColor(COLOR_FAIR, COLOR_POOR, t, clampedAlpha)
            }
            else -> {
                // -95 έως -115
                val t = ((-95f - dbm) / 20f).coerceIn(0f, 1f)
                lerpColor(COLOR_POOR, COLOR_VERY_POOR, t, clampedAlpha)
            }
        }
    }

    private fun lerpColor(startColor: Int, endColor: Int, fraction: Float, alpha: Int): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * f).toInt()
        val g = (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * f).toInt()
        val b = (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * f).toInt()
        return Color.argb(alpha, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun applyAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun toHexString(dbm: Int): String {
        val c = getDiscreteColor(dbm, 255)
        return String.format("#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c))
    }
}
