package dev.tyler.tides.data

import dev.tyler.tides.api.TidePrediction
import java.time.LocalDate

class FakePredictionStore : PredictionStore {
    private val rows = mutableMapOf<String, MutableList<TidePrediction>>()

    override suspend fun forStation(stationId: String): List<TidePrediction> =
        rows[stationId].orEmpty().sortedBy { it.time }

    override suspend fun save(stationId: String, predictions: List<TidePrediction>) {
        val existing = rows.getOrPut(stationId) { mutableListOf() }
        predictions.forEach { incoming ->
            existing.removeAll { it.time == incoming.time }
            existing.add(incoming)
        }
    }

    override suspend fun prune(stationId: String, before: LocalDate) {
        rows[stationId]?.removeAll { it.time.toLocalDate().isBefore(before) }
    }

    override suspend fun clearStation(stationId: String) {
        rows.remove(stationId)
    }
}
