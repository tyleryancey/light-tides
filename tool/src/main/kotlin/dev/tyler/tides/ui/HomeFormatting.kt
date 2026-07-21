package dev.tyler.tides.ui

import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TideType
import dev.tyler.tides.data.TideUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayTides(val date: LocalDate, val events: List<TidePrediction>)

// The repository prunes cache rows at today-1 (not today), so a day-old cache still has
// yesterday's rows in it. This drops them so the "This Week" strip stays a 7-day window
// instead of quietly growing to 8 from the second day of use onward.
fun thisWeek(predictions: List<TidePrediction>, today: LocalDate): List<TidePrediction> =
    predictions.filter { !it.time.toLocalDate().isBefore(today) }

fun groupByDay(predictions: List<TidePrediction>): List<DayTides> =
    predictions
        .groupBy { it.time.toLocalDate() }
        .toSortedMap()
        .map { (date, events) -> DayTides(date, events.sortedBy { it.time }) }

fun nextTide(predictions: List<TidePrediction>, now: LocalDateTime): TidePrediction? =
    predictions.filter { it.time >= now }.minByOrNull { it.time }

fun todaysRemaining(predictions: List<TidePrediction>, today: LocalDate, now: LocalDateTime): List<TidePrediction> =
    predictions
        .filter { it.time.toLocalDate() == today && it.time >= now }
        .sortedBy { it.time }

fun countdownText(next: TidePrediction, now: LocalDateTime): String? {
    if (!next.time.isAfter(now)) return null
    val duration = Duration.between(now, next.time)
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    return when {
        hours > 0 && minutes > 0 -> "in ${hours}h ${minutes}m"
        hours > 0 -> "in ${hours}h"
        else -> "in ${minutes}m"
    }
}

fun relativeUpdatedText(lastFetchEpochDay: Long?, today: LocalDate): String? {
    if (lastFetchEpochDay == null) return null
    val daysAgo = today.toEpochDay() - lastFetchEpochDay
    return when {
        daysAgo <= 0 -> "updated today"
        daysAgo == 1L -> "updated yesterday"
        else -> "updated $daysAgo days ago"
    }
}

// Every US state/territory abbreviation present in the bundled NOAA station
// directory, mapped to its plausible IANA zone(s). Used only to decide whether
// to show the naive-time countdown line (CLAUDE.md: drop it rather than get it
// wrong when the station's state doesn't plausibly match the phone's zone).
private val STATE_ZONES: Map<String, Set<String>> = mapOf(
    "AK" to setOf(
        "America/Anchorage", "America/Adak", "America/Nome", "America/Sitka",
        "America/Yakutat", "America/Juneau", "America/Metlakatla",
    ),
    "AL" to setOf("America/Chicago"),
    "AS" to setOf("Pacific/Pago_Pago"),
    "CA" to setOf("America/Los_Angeles"),
    "CT" to setOf("America/New_York"),
    "DC" to setOf("America/New_York"),
    "DE" to setOf("America/New_York"),
    "FL" to setOf("America/New_York", "America/Chicago"),
    "FM" to setOf("Pacific/Chuuk", "Pacific/Pohnpei", "Pacific/Kosrae"),
    "GA" to setOf("America/New_York"),
    "GU" to setOf("Pacific/Guam"),
    "HI" to setOf("Pacific/Honolulu"),
    "LA" to setOf("America/Chicago"),
    "MA" to setOf("America/New_York"),
    "MD" to setOf("America/New_York"),
    "ME" to setOf("America/New_York"),
    "MS" to setOf("America/Chicago"),
    "NC" to setOf("America/New_York"),
    "NH" to setOf("America/New_York"),
    "NJ" to setOf("America/New_York"),
    "NY" to setOf("America/New_York"),
    "OR" to setOf("America/Los_Angeles"),
    "PA" to setOf("America/New_York"),
    "PR" to setOf("America/Puerto_Rico"),
    "RI" to setOf("America/New_York"),
    "SC" to setOf("America/New_York"),
    "TX" to setOf("America/Chicago"),
    "VA" to setOf("America/New_York"),
    "VI" to setOf("America/Puerto_Rico"),
    "WA" to setOf("America/Los_Angeles"),
)

// Compares current UTC offset rather than the zone ID string, so a phone reporting an alias
// zone that observes the same rules (e.g. America/Detroit for a New_York-observing state,
// America/Juneau for Alaska) still counts as plausible, without needing every alias enumerated.
fun isPlausiblePhoneZone(
    stationState: String,
    phoneZone: ZoneId = ZoneId.systemDefault(),
    instant: Instant = Instant.now(),
): Boolean {
    val candidates = STATE_ZONES[stationState.uppercase()] ?: return false
    val phoneOffset = phoneZone.rules.getOffset(instant)
    return candidates.any { ZoneId.of(it).rules.getOffset(instant) == phoneOffset }
}

private const val FEET_PER_METER = 0.3048
private val CLOCK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun formatHeight(heightFt: Double, unit: TideUnit): String {
    val value = if (unit == TideUnit.METERS) heightFt * FEET_PER_METER else heightFt
    val suffix = if (unit == TideUnit.METERS) "m" else "ft"
    return String.format(Locale.US, "%.1f %s", value, suffix)
}

fun formatClockTime(time: LocalDateTime): String = time.format(CLOCK_TIME_FORMAT)

// The full "HIGH 5.2 ft — 4:12 PM" line, used only for the "Next:" headline.
// List rows use formatHeightTimeLine — the ▲/▼ glyph already carries H/L there.
fun formatTideLine(prediction: TidePrediction, unit: TideUnit): String {
    val label = if (prediction.type == TideType.HIGH) "HIGH" else "LOW"
    return "$label ${formatHeight(prediction.heightFt, unit)} — ${formatClockTime(prediction.time)}"
}

fun formatHeightTimeLine(prediction: TidePrediction, unit: TideUnit): String =
    "${formatHeight(prediction.heightFt, unit)} — ${formatClockTime(prediction.time)}"
