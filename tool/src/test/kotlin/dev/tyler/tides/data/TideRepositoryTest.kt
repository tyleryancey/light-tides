package dev.tyler.tides.data

import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TideType
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TideRepositoryTest {
    private val today = LocalDate.of(2026, 7, 20)

    private fun prediction(day: Int, hour: Int, height: Double, type: TideType) = TidePrediction(
        time = LocalDateTime.of(2026, 7, day, hour, 0),
        heightFt = height,
        type = type,
    )

    @Test
    fun returnsNullWhenNoStationIsSelected() = runTest {
        val repo = TideRepository(FakeTideMetaStore(), FakePredictionStore(), FakeTidesFetcher())
        assertNull(repo.loadTides(today))
    }

    @Test
    fun freshCacheReturnsWithoutRefetching() = runTest {
        val meta = FakeTideMetaStore()
        val store = FakePredictionStore()
        val fetcher = FakeTidesFetcher()
        meta.setStation("9414290", "SAN FRANCISCO (Golden Gate)", "CA")
        meta.setLastFetchEpochDay(today.toEpochDay())
        store.save("9414290", listOf(prediction(20, 4, 4.2, TideType.HIGH)))

        val repo = TideRepository(meta, store, fetcher)
        val snapshot = repo.loadTides(today)

        assertEquals(0, fetcher.callCount)
        assertEquals(false, snapshot?.stale)
        assertEquals(1, snapshot?.predictions?.size)
    }

    @Test
    fun staleCacheRefetchesAndPrunesOldRows() = runTest {
        val meta = FakeTideMetaStore()
        val store = FakePredictionStore()
        val fetcher = FakeTidesFetcher()
        meta.setStation("9414290", "SAN FRANCISCO (Golden Gate)", "CA")
        meta.setLastFetchEpochDay(today.minusDays(1).toEpochDay())
        store.save(
            "9414290",
            listOf(
                prediction(18, 4, 1.0, TideType.LOW), // older than today-1 — should be pruned
                prediction(19, 4, 2.0, TideType.HIGH), // exactly today-1 — kept
            ),
        )
        fetcher.setNextResult(Result.success(listOf(prediction(20, 4, 4.2, TideType.HIGH))))

        val repo = TideRepository(meta, store, fetcher)
        val snapshot = repo.loadTides(today)

        assertEquals(1, fetcher.callCount)
        assertEquals(today to today.plusDays(6), fetcher.lastRequestedRange)
        assertEquals(false, snapshot?.stale)
        assertEquals(today.toEpochDay(), meta.lastFetchEpochDay())
        val stationRows = store.forStation("9414290")
        assertEquals(2, stationRows.size) // today-1 kept, today added; today-2 pruned
        assertTrue(stationRows.none { it.time.toLocalDate() == LocalDate.of(2026, 7, 18) })
    }

    @Test
    fun staleCacheWithFailedRefetchReturnsCachedDataMarkedStale() = runTest {
        val meta = FakeTideMetaStore()
        val store = FakePredictionStore()
        val fetcher = FakeTidesFetcher()
        meta.setStation("9414290", "SAN FRANCISCO (Golden Gate)", "CA")
        meta.setLastFetchEpochDay(today.minusDays(1).toEpochDay())
        store.save("9414290", listOf(prediction(19, 4, 2.0, TideType.HIGH)))
        fetcher.setNextResult(Result.failure(RuntimeException("offline")))

        val repo = TideRepository(meta, store, fetcher)
        val snapshot = repo.loadTides(today)

        assertEquals(true, snapshot?.stale)
        assertEquals(1, snapshot?.predictions?.size)
        assertEquals(today.minusDays(1).toEpochDay(), snapshot?.lastFetchEpochDay)
    }

    @Test
    fun noCacheWithFailedRefetchReturnsEmptyMarkedStale() = runTest {
        val meta = FakeTideMetaStore()
        val fetcher = FakeTidesFetcher()
        meta.setStation("9414290", "SAN FRANCISCO (Golden Gate)", "CA")
        fetcher.setNextResult(Result.failure(RuntimeException("offline")))

        val repo = TideRepository(meta, FakePredictionStore(), fetcher)
        val snapshot = repo.loadTides(today)

        assertEquals(true, snapshot?.stale)
        assertTrue(snapshot?.predictions.orEmpty().isEmpty())
    }

    @Test
    fun selectingADifferentStationWipesThePreviousStationsRows() = runTest {
        val meta = FakeTideMetaStore()
        val store = FakePredictionStore()
        val repo = TideRepository(meta, store, FakeTidesFetcher())

        repo.selectStation("9414290", "SAN FRANCISCO (Golden Gate)", "CA")
        store.save("9414290", listOf(prediction(20, 4, 4.2, TideType.HIGH)))

        repo.selectStation("9447130", "SEATTLE (Madison St.), Elliott Bay", "WA")

        assertTrue(store.forStation("9414290").isEmpty())
        assertNull(meta.lastFetchEpochDay())
    }
}
