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
    // permanent failure (bad station/query) from a transient one (network/HTTP). HomeViewModel
    // also branches on it to avoid blaming a permanent NOAA error on the network.
    val staleCause: Throwable? = null,
)

class TideRepository(
    private val meta: TideMetaStore,
    private val predictions: PredictionStore,
    private val api: TidesFetcher,
) {
    // TideRepository is a process-wide singleton (see getInstance) reached independently by
    // Home's reload, Settings, and the tides-refresh job. This only ever guards fast, local
    // meta/Room operations — never the network fetch itself. An earlier version held it across
    // the whole fetch, which fixed the race below but meant a station switch could queue behind
    // a slow fetch for tens of seconds; if the screen holding that switch got torn down (e.g. the
    // user backed out of Settings) while queued, the switch was silently dropped, since a
    // suspended withLock is itself cancellable. Re-validating ownership right before the write
    // fixes the same race without ever blocking selectStation on a network call.
    private val mutex = Mutex()

    suspend fun currentStation(): SelectedStation? = meta.currentStation()

    suspend fun selectStation(stationId: String, stationName: String, stationState: String) = mutex.withLock {
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
    suspend fun loadTides(today: LocalDate): TidesSnapshot? {
        val station = meta.currentStation() ?: return null
        val cached = predictions.forStation(station.id)
        val lastFetch = meta.lastFetchEpochDay()

        if (lastFetch != null && lastFetch >= today.toEpochDay()) {
            return TidesSnapshot(station, cached, lastFetch, stale = false)
        }

        val fetched = api.fetchPredictions(station.id, today, today.plusDays(6))
        return fetched.fold(
            onSuccess = { fresh ->
                // The user may have switched stations while this fetch was in flight. If so, this
                // data belongs to a station that's no longer selected — write it and we'd stamp
                // the NEW station's (empty) cache as fresh with the OLD station's rows never
                // actually attached to it. Check-and-write atomically so a selectStation landing
                // in between sees a consistent before-or-after state, not a torn one.
                val wrote = mutex.withLock {
                    if (meta.currentStation()?.id == station.id) {
                        predictions.prune(station.id, before = today.minusDays(1))
                        predictions.save(station.id, fresh)
                        meta.setLastFetchEpochDay(today.toEpochDay())
                        true
                    } else {
                        false
                    }
                }
                if (wrote) {
                    TidesSnapshot(station, predictions.forStation(station.id), today.toEpochDay(), stale = false)
                } else {
                    TidesSnapshot(station, cached, lastFetch, stale = true)
                }
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
