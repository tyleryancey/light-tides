package dev.tyler.tides.data

import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TidesFetcher
import java.time.LocalDate

class FakeTidesFetcher(
    private var result: Result<List<TidePrediction>> = Result.success(emptyList()),
) : TidesFetcher {
    var callCount: Int = 0
        private set
    var lastRequestedRange: Pair<LocalDate, LocalDate>? = null
        private set

    fun setNextResult(result: Result<List<TidePrediction>>) {
        this.result = result
    }

    override suspend fun fetchPredictions(
        stationId: String,
        beginDate: LocalDate,
        endDate: LocalDate,
    ): Result<List<TidePrediction>> {
        callCount++
        lastRequestedRange = beginDate to endDate
        return result
    }
}
