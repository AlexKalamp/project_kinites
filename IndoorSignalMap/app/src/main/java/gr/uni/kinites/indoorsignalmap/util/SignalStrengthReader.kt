package gr.uni.kinites.indoorsignalmap.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

data class SignalReading(
    val dbm: Int,
    val networkType: String,
    val isLiveHardwareReading: Boolean
)

/**
 * Υπεύθυνος ανάγνωσης της ισχύος σήματος κινητής τηλεφωνίας (2G/3G/4G/5G) σε dBm
 * μέσω του TelephonyManager API και του TelephonyCallback (για Android 12+ / API 31+).
 */
class SignalStrengthReader(private val context: Context) {

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    @Volatile
    private var latestCallbackDbm: Int? = null

    @Volatile
    private var latestCallbackNetType: String? = null

    private var telephonyCallback: Any? = null

    var onLiveSignalChanged: ((SignalReading) -> Unit)? = null

    fun hasPermissions(): Boolean {
        val fineLoc = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLoc = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLoc || coarseLoc
    }

    /**
     * Ξεκινά την παρακολούθηση σε πραγματικό χρόνο μέσω TelephonyCallback (API 31+).
     */
    fun startListening() {
        if (!hasPermissions()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback == null) {
            try {
                val callback = ModernSignalCallback()
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
                telephonyCallback = callback
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = telephonyCallback as? TelephonyCallback
            if (cb != null) {
                try {
                    telephonyManager?.unregisterTelephonyCallback(cb)
                } catch (_: Exception) {
                }
            }
            telephonyCallback = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class ModernSignalCallback : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            val extracted = extractFromSignalStrength(signalStrength)
            if (extracted != null) {
                latestCallbackDbm = extracted.first
                latestCallbackNetType = extracted.second
                onLiveSignalChanged?.invoke(
                    SignalReading(
                        dbm = extracted.first,
                        networkType = extracted.second,
                        isLiveHardwareReading = true
                    )
                )
            }
        }
    }

    /**
     * Ανακτά σε πραγματικό χρόνο την τρέχουσα ισχύ σήματος σε dBm.
     * Ελέγχει κατά σειρά:
     * 1. telephonyManager.allCellInfo (Registered / Serving Cell)
     * 2. telephonyManager.signalStrength (API 28+)
     * 3. Τελευταία μέτρηση από το TelephonyCallback
     * Αν δεν υπάρχει κάρτα SIM ή έγκυρη ένδειξη κυψέλης, επιστρέφει null ώστε η εφαρμογή
     * να χρησιμοποιήσει την τιμή προσομοίωσης ή -120 dBm (No Signal).
     */
    @SuppressLint("MissingPermission")
    fun readCurrentSignal(): SignalReading? {
        val tm = telephonyManager ?: return null

        // 1. Προσπάθεια ανάγνωσης από allCellInfo (απαιτεί ACCESS_FINE_LOCATION)
        if (hasPermissions()) {
            try {
                val cellInfos: List<CellInfo>? = tm.allCellInfo
                if (!cellInfos.isNullOrEmpty()) {
                    // Προτιμάμε την καταχωρημένη κυψέλη (isRegistered)
                    val registered = cellInfos.filter { it.isRegistered }
                    val candidates = if (registered.isNotEmpty()) registered else cellInfos
                    for (cell in candidates) {
                        val parsed = extractFromCellInfo(cell)
                        if (parsed != null && isValidDbm(parsed.first)) {
                            return SignalReading(
                                dbm = parsed.first,
                                networkType = parsed.second,
                                isLiveHardwareReading = true
                            )
                        }
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        // 2. Προσπάθεια ανάγνωσης από tm.signalStrength (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val ss = tm.signalStrength
                if (ss != null) {
                    val parsed = extractFromSignalStrength(ss)
                    if (parsed != null && isValidDbm(parsed.first)) {
                        return SignalReading(
                            dbm = parsed.first,
                            networkType = parsed.second,
                            isLiveHardwareReading = true
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Τελευταία τιμή από το TelephonyCallback
        val cbDbm = latestCallbackDbm
        val cbNet = latestCallbackNetType
        if (cbDbm != null && cbNet != null && isValidDbm(cbDbm)) {
            return SignalReading(
                dbm = cbDbm,
                networkType = cbNet,
                isLiveHardwareReading = true
            )
        }

        return null
    }

    private fun extractFromCellInfo(cellInfo: CellInfo): Pair<Int, String>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
            val dbm = cellInfo.cellSignalStrength.dbm
            if (isValidDbm(dbm)) return dbm to "5G NR"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoTdscdma) {
            val dbm = cellInfo.cellSignalStrength.dbm
            if (isValidDbm(dbm)) return dbm to "3G TD-SCDMA"
        }
        return when (cellInfo) {
            is CellInfoLte -> {
                val dbm = cellInfo.cellSignalStrength.dbm
                if (isValidDbm(dbm)) dbm to "4G LTE" else null
            }
            is CellInfoWcdma -> {
                val dbm = cellInfo.cellSignalStrength.dbm
                if (isValidDbm(dbm)) dbm to "3G WCDMA" else null
            }
            is CellInfoGsm -> {
                val dbm = cellInfo.cellSignalStrength.dbm
                if (isValidDbm(dbm)) dbm to "2G GSM" else null
            }
            is CellInfoCdma -> {
                val dbm = cellInfo.cellSignalStrength.dbm
                if (isValidDbm(dbm)) dbm to "2G/3G CDMA" else null
            }
            else -> null
        }
    }

    private fun extractFromSignalStrength(signalStrength: SignalStrength): Pair<Int, String>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val strengths: List<CellSignalStrength> = signalStrength.cellSignalStrengths
            for (css in strengths) {
                val dbm = css.dbm
                if (isValidDbm(dbm)) {
                    val type = when (css.javaClass.simpleName) {
                        "CellSignalStrengthNr" -> "5G NR"
                        "CellSignalStrengthLte" -> "4G LTE"
                        "CellSignalStrengthWcdma", "CellSignalStrengthTdscdma" -> "3G UMTS"
                        "CellSignalStrengthGsm", "CellSignalStrengthCdma" -> "2G GSM"
                        else -> resolveNetworkTypeLabel()
                    }
                    return dbm to type
                }
            }
        }
        // Fallback για API 26-28 μέσω GSM ASU αν υπάρχει
        @Suppress("DEPRECATION")
        val gsmAsu = signalStrength.gsmSignalStrength
        if (gsmAsu in 0..31) {
            val dbm = -113 + 2 * gsmAsu
            return dbm to resolveNetworkTypeLabel()
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun resolveNetworkTypeLabel(): String {
        val tm = telephonyManager ?: return "Cellular"
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
            when (type) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
                else -> "4G/5G"
            }
        } catch (_: Exception) {
            "4G/5G"
        }
    }

    private fun isValidDbm(dbm: Int): Boolean {
        return dbm != CellInfo.UNAVAILABLE && dbm in -140..-30
    }
}
