package dev.tyler.tides.data

import dev.tyler.tides.api.TidePrediction
import java.time.LocalDate

interface PredictionStore {
    suspend fun forStation(stationId: String): List<TidePrediction>
    suspend fun save(stationId: String, predictions: List<TidePrediction>)
    suspend fun prune(stationId: String, before: LocalDate)
    suspend fun clearStation(stationId: String)
}
