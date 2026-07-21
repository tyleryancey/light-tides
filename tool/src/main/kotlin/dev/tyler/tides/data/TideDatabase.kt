package dev.tyler.tides.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TideType
import dev.tyler.tides.api.toApiCode
import dev.tyler.tides.time.formatStationLocalTime
import dev.tyler.tides.time.parseStationLocalTime
import java.time.LocalDate

@Entity(tableName = "predictions", primaryKeys = ["stationId", "t"])
data class PredictionEntity(
    val stationId: String,
    val t: String,
    val heightFt: Double,
    val type: String,
)

@Dao
interface PredictionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PredictionEntity>)

    @Query("SELECT * FROM predictions WHERE stationId = :stationId ORDER BY t ASC")
    suspend fun forStation(stationId: String): List<PredictionEntity>

    @Query("DELETE FROM predictions WHERE stationId = :stationId AND t < :cutoff")
    suspend fun prune(stationId: String, cutoff: String)

    @Query("DELETE FROM predictions WHERE stationId = :stationId")
    suspend fun clearStation(stationId: String)
}

@Database(entities = [PredictionEntity::class], version = 1, exportSchema = false)
abstract class TideDatabase : RoomDatabase() {
    abstract fun predictionDao(): PredictionDao

    companion object {
        const val DATABASE_NAME = "tides.db"
    }
}

class RoomPredictionStore(private val dao: PredictionDao) : PredictionStore {
    override suspend fun forStation(stationId: String): List<TidePrediction> =
        dao.forStation(stationId).mapNotNull { it.toDomainOrNull() }

    override suspend fun save(stationId: String, predictions: List<TidePrediction>) {
        dao.insertAll(predictions.map { it.toEntity(stationId) })
    }

    override suspend fun prune(stationId: String, before: LocalDate) {
        dao.prune(stationId, formatStationLocalTime(before.atStartOfDay()))
    }

    override suspend fun clearStation(stationId: String) {
        dao.clearStation(stationId)
    }
}

private fun TidePrediction.toEntity(stationId: String) = PredictionEntity(
    stationId = stationId,
    t = formatStationLocalTime(time),
    heightFt = heightFt,
    type = type.toApiCode(),
)

private fun PredictionEntity.toDomainOrNull(): TidePrediction? {
    val tideType = TideType.fromApi(type) ?: return null
    val parsedTime = runCatching { parseStationLocalTime(t) }.getOrNull() ?: return null
    return TidePrediction(time = parsedTime, heightFt = heightFt, type = tideType)
}
