package dev.tyler.tides.time

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StationLocalTimeTest {
    @Test
    fun parsesNoaaNaiveTimestamp() {
        val parsed = parseStationLocalTime("2026-07-20 04:43")
        assertEquals(LocalDateTime.of(2026, 7, 20, 4, 43), parsed)
    }

    @Test
    fun formatRoundTripsBackToTheSameString() {
        val raw = "2026-07-26 21:57"
        assertEquals(raw, formatStationLocalTime(parseStationLocalTime(raw)))
    }

    @Test
    fun singleDigitHourAndMinuteAreZeroPadded() {
        val time = LocalDateTime.of(2026, 1, 5, 2, 3)
        assertEquals("2026-01-05 02:03", formatStationLocalTime(time))
    }
}
