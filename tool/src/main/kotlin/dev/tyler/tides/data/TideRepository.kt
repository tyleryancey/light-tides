package dev.tyler.tides.data

import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TidesFetcher
import java.time.LocalDate

data class TidesSnapshot(
    val station: SelectedStation,
    val predictions: List<TidePrediction>,
    val lastFetchEpochDay: Long?,
    val stale: Boolean,
)

class TideRepository(
    private val meta: TideMetaStore,
    private val predictions: PredictionStore,
    private val api: TidesFetcher,
) {
    suspend fun selectStation(stationId: String, stationName: String) {
        val previous = meta.currentStation()
        if (previous != null && previous.id != stationId) {
            predictions.clearStation(previous.id)
        }
        meta.setStation(stationId, stationName)
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
                predictions.prune(station.id, before = today.minusDays(1))
                predictions.save(station.id, fresh)
                val refreshed = predictions.forStation(station.id)
                meta.setLastFetchEpochDay(today.toEpochDay())
                TidesSnapshot(station, refreshed, today.toEpochDay(), stale = false)
            },
            onFailure = {
                TidesSnapshot(station, cached, lastFetch, stale = true)
            },
        )
    }
}
