package dev.tyler.tides.ui

import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TideType
import dev.tyler.tides.data.TideUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeFormattingTest {
    private fun tide(day: Int, hour: Int, minute: Int, height: Double, type: TideType) = TidePrediction(
        time = LocalDateTime.of(2026, 7, day, hour, minute),
        heightFt = height,
        type = type,
    )

    private val weekOfPredictions = listOf(
        tide(20, 4, 43, 4.222, TideType.HIGH),
        tide(20, 10, 25, 1.349, TideType.LOW),
        tide(20, 17, 24, 5.773, TideType.HIGH),
        tide(21, 0, 5, 1.359, TideType.LOW),
        tide(21, 6, 10, 3.767, TideType.HIGH),
    )

    @Test
    fun thisWeekDropsYesterdaysLeftoverCacheRowsButKeepsTodayAndLater() {
        val today = LocalDate.of(2026, 7, 20)
        val withYesterday = listOf(
            tide(19, 20, 0, 2.0, TideType.HIGH), // yesterday — repository prunes at today-1, so this lingers
        ) + weekOfPredictions

        val filtered = thisWeek(withYesterday, today)

        assertEquals(weekOfPredictions, filtered)
        assertTrue(filtered.none { it.time.toLocalDate() == LocalDate.of(2026, 7, 19) })
    }

    @Test
    fun groupByDaySortsDaysAndEventsWithinEachDay() {
        val grouped = groupByDay(weekOfPredictions.shuffled())

        assertEquals(listOf(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21)), grouped.map { it.date })
        assertEquals(listOf(4 to 43, 10 to 25, 17 to 24), grouped[0].events.map { it.time.hour to it.time.minute })
    }

    @Test
    fun nextTideIsTheFirstEventAtOrAfterNow() {
        val now = LocalDateTime.of(2026, 7, 20, 11, 0)
        assertEquals(tide(20, 17, 24, 5.773, TideType.HIGH), nextTide(weekOfPredictions, now))
    }

    @Test
    fun nextTideReturnsNullWhenNothingIsUpcoming() {
        val now = LocalDateTime.of(2026, 7, 22, 0, 0)
        assertNull(nextTide(weekOfPredictions, now))
    }

    @Test
    fun todaysRemainingExcludesPastEventsAndOtherDays() {
        val now = LocalDateTime.of(2026, 7, 20, 11, 0)
        val remaining = todaysRemaining(weekOfPredictions, today = LocalDate.of(2026, 7, 20), now = now)
        assertEquals(listOf(tide(20, 17, 24, 5.773, TideType.HIGH)), remaining)
    }

    @Test
    fun countdownTextFormatsHoursAndMinutes() {
        val now = LocalDateTime.of(2026, 7, 20, 14, 44)
        val next = tide(20, 17, 24, 5.773, TideType.HIGH)
        assertEquals("in 2h 40m", countdownText(next, now))
    }

    @Test
    fun countdownTextDropsZeroHoursOrZeroMinutes() {
        val next = tide(20, 17, 24, 5.773, TideType.HIGH)
        assertEquals("in 45m", countdownText(next, now = LocalDateTime.of(2026, 7, 20, 16, 39)))
        assertEquals("in 2h", countdownText(next, now = LocalDateTime.of(2026, 7, 20, 15, 24)))
    }

    @Test
    fun countdownTextReturnsNullForNonFutureTimes() {
        val next = tide(20, 17, 24, 5.773, TideType.HIGH)
        assertNull(countdownText(next, now = next.time))
        assertNull(countdownText(next, now = next.time.plusMinutes(1)))
    }

    @Test
    fun relativeUpdatedTextCoversTodayYesterdayAndOlder() {
        val today = LocalDate.of(2026, 7, 20)
        assertNull(relativeUpdatedText(null, today))
        assertEquals("updated today", relativeUpdatedText(today.toEpochDay(), today))
        assertEquals("updated yesterday", relativeUpdatedText(today.minusDays(1).toEpochDay(), today))
        assertEquals("updated 3 days ago", relativeUpdatedText(today.minusDays(3).toEpochDay(), today))
    }

    @Test
    fun plausiblePhoneZoneMatchesStationState() {
        // Fixed midsummer instant — avoids any real DST-transition-day edge case.
        val instant = java.time.Instant.parse("2026-07-20T12:00:00Z")
        assertTrue(isPlausiblePhoneZone("CA", ZoneId.of("America/Los_Angeles"), instant))
        assertTrue(isPlausiblePhoneZone("hi", ZoneId.of("Pacific/Honolulu"), instant))
        assertFalse(isPlausiblePhoneZone("CA", ZoneId.of("America/New_York"), instant))
        assertFalse(isPlausiblePhoneZone("ZZ", ZoneId.of("America/Los_Angeles"), instant))
    }

    @Test
    fun plausiblePhoneZoneMatchesAliasZonesByOffsetNotId() {
        val instant = java.time.Instant.parse("2026-07-20T12:00:00Z")
        // Neither zone is in NY's candidate set, but both observe identical rules to
        // America/New_York year-round — offset comparison catches them without enumeration.
        assertTrue(isPlausiblePhoneZone("NY", ZoneId.of("America/Detroit"), instant))
        assertTrue(isPlausiblePhoneZone("NY", ZoneId.of("America/Kentucky/Louisville"), instant))
    }

    @Test
    fun formatHeightConvertsFeetToMetersAndRoundsToOneDecimal() {
        assertEquals("5.2 ft", formatHeight(5.213, TideUnit.FEET))
        assertEquals("1.6 m", formatHeight(5.213, TideUnit.METERS))
        assertEquals("-0.1 ft", formatHeight(-0.111, TideUnit.FEET))
    }

    @Test
    fun formatClockTimeUses12HourClock() {
        assertEquals("4:12 AM", formatClockTime(LocalDateTime.of(2026, 7, 20, 4, 12)))
        assertEquals("4:12 PM", formatClockTime(LocalDateTime.of(2026, 7, 20, 16, 12)))
        assertEquals("12:00 AM", formatClockTime(LocalDateTime.of(2026, 7, 20, 0, 0)))
    }

    @Test
    fun formatTideLineMatchesCLAUDEMdExampleShape() {
        val next = tide(20, 16, 12, 5.213, TideType.HIGH)
        assertEquals("HIGH 5.2 ft — 4:12 PM", formatTideLine(next, TideUnit.FEET))
    }

    @Test
    fun formatHeightTimeLineOmitsTheHighLowLabel() {
        val next = tide(20, 16, 12, 5.213, TideType.HIGH)
        assertEquals("5.2 ft — 4:12 PM", formatHeightTimeLine(next, TideUnit.FEET))
    }
}
