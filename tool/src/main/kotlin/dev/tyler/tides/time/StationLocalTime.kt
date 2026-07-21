package dev.tyler.tides.time

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// NOAA's lst_ldt timestamps are the station's naive local clock — no zone info.
// Never convert through Instant/ZonedDateTime; store and display this verbatim.
private val STATION_LOCAL_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun parseStationLocalTime(raw: String): LocalDateTime =
    LocalDateTime.parse(raw, STATION_LOCAL_TIME_FORMAT)

fun formatStationLocalTime(time: LocalDateTime): String =
    time.format(STATION_LOCAL_TIME_FORMAT)
