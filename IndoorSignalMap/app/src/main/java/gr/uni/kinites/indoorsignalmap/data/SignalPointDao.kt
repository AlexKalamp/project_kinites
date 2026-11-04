package gr.uni.kinites.indoorsignalmap.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalPointDao {

    @Query("SELECT * FROM signal_points WHERE floorId = :floorId ORDER BY timestamp ASC")
    fun getPointsForFloor(floorId: Int): Flow<List<SignalPointEntity>>

    @Query("SELECT * FROM signal_points ORDER BY floorId ASC, timestamp ASC")
    suspend fun getAllPoints(): List<SignalPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: SignalPointEntity): Long

    @Query("DELETE FROM signal_points WHERE id = (SELECT id FROM signal_points WHERE floorId = :floorId ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastPointForFloor(floorId: Int)

    @Query("DELETE FROM signal_points WHERE floorId = :floorId")
    suspend fun clearFloor(floorId: Int)

    @Query("DELETE FROM signal_points")
    suspend fun clearAll()
}
