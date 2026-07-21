package dev.tyler.tides.api

import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TidesApiParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    @Test
    fun parsesRealPredictionsFixture() {
        val body = fixture("predictions-9414290.json")
        val response = json.decodeFromString<DataGetterResponse>(body)
        val predictions = response.toPredictionsOrThrow(httpSuccess = true, rawBody = body)

        assertEquals(27, predictions.size)
        val first = predictions.first()
        assertEquals(LocalDateTime.of(2026, 7, 20, 4, 43), first.time)
        assertEquals(4.222, first.heightFt)
        assertEquals(TideType.HIGH, first.type)

        // a real negative low tide must parse correctly, not get filtered out
        val negativeLow = predictions.first { it.heightFt < 0 }
        assertEquals(TideType.LOW, negativeLow.type)
    }

    @Test
    fun throwsOnErrorKeyFixtureEvenThoughItReportsHttp200() {
        val body = fixture("predictions-error.json")
        val response = json.decodeFromString<DataGetterResponse>(body)

        val thrown = assertFailsWith<TidesStationError> {
            response.toPredictionsOrThrow(httpSuccess = true, rawBody = body)
        }
        assertTrue(thrown.message!!.contains("Predictions data"))
    }

    @Test
    fun throwsHttpErrorNotStationErrorOnNonSuccessStatusWithoutAnErrorKey() {
        // e.g. a transient 503 with a plain-text body — distinct from a NOAA error-key payload,
        // since TidesJob.toJobResult retries the former but treats the latter as permanent.
        val response = DataGetterResponse(predictions = null, error = null)

        assertFailsWith<TidesHttpError> {
            response.toPredictionsOrThrow(httpSuccess = false, rawBody = "Service Unavailable")
        }
    }

    @Test
    fun malformedRowsAreSkippedNotCrashed() {
        val response = DataGetterResponse(
            predictions = listOf(
                PredictionDto(t = "2026-07-20 04:43", v = "not-a-number", type = "H"),
                PredictionDto(t = "2026-07-20 10:25", v = "1.349", type = "X"),
                PredictionDto(t = "not-a-time", v = "1.349", type = "L"),
                PredictionDto(t = "2026-07-20 17:24", v = "5.773", type = "H"),
            ),
        )

        val predictions = response.toPredictionsOrThrow(httpSuccess = true, rawBody = "")

        assertEquals(1, predictions.size)
        assertEquals(TideType.HIGH, predictions.single().type)
    }
}
