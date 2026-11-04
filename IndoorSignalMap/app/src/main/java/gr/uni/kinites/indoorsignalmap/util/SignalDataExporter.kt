package gr.uni.kinites.indoorsignalmap.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import gr.uni.kinites.indoorsignalmap.data.SignalPointEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportResult(
    val internalFilePath: String,
    val downloadsFileName: String?,
    val recordCount: Int
)

/**
 * Υλοποιεί την εξαγωγή (Export) των μετρήσεων του χάρτη σε αρχείο .csv ή .json
 * τόσο στην εσωτερική μνήμη της εφαρμογής (internal storage) όσο και στον δημόσιο
 * φάκελο Downloads της συσκευής (Bonus Επίπεδο).
 */
object SignalDataExporter {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun exportToCsv(context: Context, points: List<SignalPointEntity>): ExportResult {
        val sb = StringBuilder()
        sb.appendLine("id,floor_id,floor_name,normalized_x,normalized_y,dbm,quality,hex_color,network_type,timestamp_iso")
        for (p in points) {
            val floorName = floorName(p.floorId)
            val quality = SignalColorMapper.getQualityLabel(p.dbm)
            val hex = SignalColorMapper.toHexString(p.dbm)
            val dateStr = dateFormatter.format(Date(p.timestamp))
            sb.appendLine(
                "${p.id},${p.floorId},\"$floorName\"," +
                    String.format(Locale.US, "%.4f,%.4f", p.normalizedX, p.normalizedY) +
                    ",${p.dbm},\"$quality\",$hex,\"${p.networkType}\",\"$dateStr\""
            )
        }
        val content = sb.toString()
        val fileName = "indoor_signal_map_${System.currentTimeMillis()}.csv"
        return saveExportFile(context, fileName, "text/csv", content, points.size)
    }

    fun exportToJson(context: Context, points: List<SignalPointEntity>): ExportResult {
        val root = JSONObject()
        root.put("project", "Indoor Mobile Signal Map")
        root.put("exported_at", dateFormatter.format(Date()))
        root.put("total_measurements", points.size)

        val arr = JSONArray()
        for (p in points) {
            val item = JSONObject().apply {
                put("id", p.id)
                put("floorId", p.floorId)
                put("floorName", floorName(p.floorId))
                put("normalizedX", p.normalizedX.toDouble())
                put("normalizedY", p.normalizedY.toDouble())
                put("dbm", p.dbm)
                put("quality", SignalColorMapper.getQualityLabel(p.dbm))
                put("colorHex", SignalColorMapper.toHexString(p.dbm))
                put("networkType", p.networkType)
                put("timestamp", p.timestamp)
                put("timestampFormatted", dateFormatter.format(Date(p.timestamp)))
            }
            arr.put(item)
        }
        root.put("measurements", arr)

        val content = root.toString(2)
        val fileName = "indoor_signal_map_${System.currentTimeMillis()}.json"
        return saveExportFile(context, fileName, "application/json", content, points.size)
    }

    private fun saveExportFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
        count: Int
    ): ExportResult {
        // 1. Αποθήκευση στην εσωτερική μνήμη της εφαρμογής (Internal Storage)
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val internalFile = File(exportDir, fileName)
        internalFile.writeText(content, Charsets.UTF_8)

        // 2. Παράλληλη αποθήκευση και στον φάκελο Downloads μέσω MediaStore (API 29+)
        var savedDownloadName: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    savedDownloadName = "Downloads/$fileName"
                }
            } catch (_: Exception) {
            }
        }

        return ExportResult(
            internalFilePath = internalFile.absolutePath,
            downloadsFileName = savedDownloadName,
            recordCount = count
        )
    }

    private fun floorName(floorId: Int): String = when (floorId) {
        0 -> "Ισόγειο (Ground Floor)"
        1 -> "1ος Όροφος (1st Floor)"
        2 -> "2ος Όροφος (2nd Floor)"
        else -> "Όροφος $floorId"
    }
}
