package dev.tyler.tides.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TidesApi
import dev.tyler.tides.api.TidesFetcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class TidesSnapshot(
    val station: SelectedStation,
    val predictions: List<TidePrediction>,
    val lastFetchEpochDay: Long?,
    val stale: Boolean,
    // Only set when stale from a failed fetch — lets callers that care (TidesJob) distinguish a
    // permanent failure (bad station/query) from a transient one (network/HTTP). The UI ignores it.
    val staleCause: Throwable? = null,
)

class TideRepository(
    private val meta: TideMetaStore,
    private val predictions: PredictionStore,
    private val api: TidesFetcher,
) {
    // TideRepository is a process-wide singleton (see getInstance) reached independently by
    // Home's reload, Settings, and the tides-refresh job — without this, a job run landing at
    // the same moment as an on-open reload on a stale day double-fetches the same station.
    private val loadMutex = Mutex()

    suspend fun currentStation(): SelectedStation? = meta.currentStation()

    suspend fun selectStation(stationId: String, stationName: String, stationState: String) {
        val previous = meta.currentStation()
        // setStation first: if this coroutine is cancelled (e.g. Settings closes mid-write)
        // right after, the new station is already selected with lastFetchEpochDay cleared, so
        // the next load just re-fetches it — self-healing. Clearing first would instead risk
        // leaving the OLD station selected with its rows already wiped and lastFetchEpochDay
        // still valid, which reads as a real, empty week rather than stale data needing a retry.
        meta.setStation(stationId, stationName, stationState)
        if (previous != null && previous.id != stationId) {
            predictions.clearStation(previous.id)
        }
    }

    /** Cache-first: returns null only when no station has been selected yet. */
    suspend fun loadTides(today: LocalDate): TidesSnapshot? = loadMutex.withLock {
        val station = meta.currentStation() ?: return@withLock null
        val cached = predictions.forStation(station.id)
        val lastFetch = meta.lastFetchEpochDay()

        if (lastFetch != null && lastFetch >= today.toEpochDay()) {
            return@withLock TidesSnapshot(station, cached, lastFetch, stale = false)
        }

        val fetched = api.fetchPredictions(station.id, today, today.plusDays(6))
        fetched.fold(
            onSuccess = { fresh ->
                predictions.prune(station.id, before = today.minusDays(1))
                predictions.save(station.id, fresh)
                val refreshed = predictions.forStation(station.id)
                meta.setLastFetchEpochDay(today.toEpochDay())
                TidesSnapshot(station, refreshed, today.toEpochDay(), stale = false)
            },
            onFailure = { cause ->
                TidesSnapshot(station, cached, lastFetch, stale = true, staleCause = cause)
            },
        )
    }

    companion object {
        @Volatile
        private var instance: TideRepository? = null

        fun getInstance(
            dataStore: DataStore<Preferences>,
            databaseProvider: () -> TideDatabase,
        ): TideRepository = instance ?: synchronized(this) {
            instance ?: run {
                val db = databaseProvider()
                TideRepository(
                    meta = DataStoreTideMetaStore(dataStore),
                    predictions = RoomPredictionStore(db.predictionDao()),
                    api = TidesApi(),
                ).also { instance = it }
            }
        }
    }
}
