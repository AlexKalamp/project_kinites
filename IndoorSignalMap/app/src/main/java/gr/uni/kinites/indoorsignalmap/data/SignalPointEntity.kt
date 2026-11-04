package gr.uni.kinites.indoorsignalmap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_points")
data class SignalPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val floorId: Int,          // 0 = Ισόγειο, 1 = 1ος Όροφος, 2 = 2ος Όροφος
    val normalizedX: Float,    // 0.0 .. 1.0 relative to floor plan width
    val normalizedY: Float,    // 0.0 .. 1.0 relative to floor plan height
    val dbm: Int,              // Signal strength in dBm (e.g. -120 to -40)
    val networkType: String,   // e.g. "4G LTE", "5G NR", "3G", "2G", "Simulated"
    val timestamp: Long = System.currentTimeMillis()
)
