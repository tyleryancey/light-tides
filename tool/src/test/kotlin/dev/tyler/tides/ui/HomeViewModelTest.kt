package dev.tyler.tides.ui

import dev.tyler.tides.api.TideType
import dev.tyler.tides.api.TidesHttpError
import dev.tyler.tides.api.TidesStationError
import dev.tyler.tides.data.SelectedStation
import dev.tyler.tides.data.TidesSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HomeViewModelTest {
    private val today = LocalDate.of(2026, 7, 20)
    private val station = SelectedStation(id = "9414290", name = "SAN FRANCISCO (Golden Gate)", state = "CA")

    private fun tide(hour: Int, height: Double, type: TideType) = dev.tyler.tides.api.TidePrediction(
        time = LocalDateTime.of(2026, 7, 20, hour, 0),
        heightFt = height,
        type = type,
    )

    @Test
    fun noSnapshotMeansNeedsStation() {
        assertIs<HomeScreenMode.NeedsStation>(null.toScreenMode(today))
    }

    @Test
    fun freshSnapshotIsRenderedAsContent() {
        val snapshot = TidesSnapshot(
            station, predictions = listOf(tide(4, 4.2, TideType.HIGH)),
            lastFetchEpochDay = today.toEpochDay(), stale = false,
        )
        val mode = assertIs<HomeScreenMode.Content>(snapshot.toScreenMode(today))
        assertEquals("9414290", mode.stationId)
        assertNull(mode.updatedRelative) // fresh — no "updated X ago" stamp
    }

    @Test
    fun staleButNonEmptyRendersContentWithAStampNotAnError() {
        val snapshot = TidesSnapshot(
            station, predictions = listOf(tide(4, 4.2, TideType.HIGH)),
            lastFetchEpochDay = today.minusDays(1).toEpochDay(), stale = true,
        )
        val mode = assertIs<HomeScreenMode.Content>(snapshot.toScreenMode(today))
        assertEquals("updated yesterday", mode.updatedRelative)
    }

    @Test
    fun sublineIsTheCountdownWhenThePhoneZoneIsPlausible() {
        val snapshot = TidesSnapshot(
            station, predictions = listOf(tide(4, 4.2, TideType.HIGH)),
            lastFetchEpochDay = today.toEpochDay(), stale = false,
        )
        val mode = assertIs<HomeScreenMode.Content>(
            snapshot.toScreenMode(today, now = LocalDateTime.of(2026, 7, 20, 2, 0), zoneIsPlausible = { true }),
        )
        assertEquals("in 2h", mode.subline)
    }

    @Test
    fun sublineLabelsStationLocalTimeWhenThePhoneZoneIsNot() {
        val snapshot = TidesSnapshot(
            station, predictions = listOf(tide(4, 4.2, TideType.HIGH)),
            lastFetchEpochDay = today.toEpochDay(), stale = false,
        )
        val mode = assertIs<HomeScreenMode.Content>(
            snapshot.toScreenMode(today, now = LocalDateTime.of(2026, 7, 20, 2, 0), zoneIsPlausible = { false }),
        )
        assertEquals("Times are station-local", mode.subline)
    }

    @Test
    fun emptyAndStaleFromATransientFailureShowsTheOfflineMessage() {
        val snapshot = TidesSnapshot(
            station, predictions = emptyList(), lastFetchEpochDay = null,
            stale = true, staleCause = TidesHttpError("503"),
        )
        val mode = assertIs<HomeScreenMode.ErrorState>(snapshot.toScreenMode(today))
        assertEquals(OFFLINE_EMPTY_MESSAGE, mode.message)
    }

    @Test
    fun emptyAndStaleFromAPermanentStationErrorDoesNotBlameTheNetwork() {
        val snapshot = TidesSnapshot(
            station, predictions = emptyList(), lastFetchEpochDay = null,
            stale = true, staleCause = TidesStationError("bad datum"),
        )
        val mode = assertIs<HomeScreenMode.ErrorState>(snapshot.toScreenMode(today))
        assertEquals(STATION_ERROR_MESSAGE, mode.message)
    }
}
