package dev.tyler.tides.api

import dev.tyler.tides.time.parseStationLocalTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class TideType {
    HIGH,
    LOW,
    ;

    companion object {
        fun fromApi(raw: String): TideType? = when (raw) {
            "H" -> HIGH
            "L" -> LOW
            else -> null
        }
    }
}

fun TideType.toApiCode(): String = when (this) {
    TideType.HIGH -> "H"
    TideType.LOW -> "L"
}

data class TidePrediction(
    val time: LocalDateTime,
    val heightFt: Double,
    val type: TideType,
)

// NOAA's `{"error":{...}}` payload means the query itself is invalid (bad station/datum/etc.) —
// permanent, retrying won't help. An HTTP-level failure (including 5xx) may well be transient.
// Job scheduling (TidesJob.toJobResult) relies on this split to avoid retrying forever on a
// permanently bad station instead of resuming the normal daily cadence.
sealed class TidesApiException(message: String) : Exception(message)
class TidesStationError(message: String) : TidesApiException(message)
class TidesHttpError(message: String) : TidesApiException(message)

interface TidesFetcher {
    suspend fun fetchPredictions(
        stationId: String,
        beginDate: LocalDate,
        endDate: LocalDate,
    ): Result<List<TidePrediction>>
}

@Serializable
internal data class DataGetterResponse(
    val predictions: List<PredictionDto>? = null,
    val error: ErrorDetail? = null,
)

@Serializable
internal data class ErrorDetail(val message: String)

@Serializable
internal data class PredictionDto(
    val t: String,
    val v: String,
    val type: String,
)

private val REQUEST_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

class TidesApi(private val application: String = "dev.tyler.tides") : TidesFetcher {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp)

    override suspend fun fetchPredictions(
        stationId: String,
        beginDate: LocalDate,
        endDate: LocalDate,
    ): Result<List<TidePrediction>> = runCatching {
        val response = client.get("https://api.tidesandcurrents.noaa.gov/api/prod/datagetter") {
            parameter("product", "predictions")
            parameter("datum", "MLLW")
            parameter("interval", "hilo")
            parameter("units", "english")
            parameter("time_zone", "lst_ldt")
            parameter("format", "json")
            parameter("station", stationId)
            parameter("begin_date", beginDate.format(REQUEST_DATE_FORMAT))
            parameter("end_date", endDate.format(REQUEST_DATE_FORMAT))
            parameter("application", application)
        }

        val bodyText = response.bodyAsText()
        val parsed = json.decodeFromString<DataGetterResponse>(bodyText)
        parsed.toPredictionsOrThrow(httpSuccess = response.status.isSuccess(), rawBody = bodyText)
    }

    fun close() {
        client.close()
    }
}

// datagetter returns {"error":{...}} with HTTP 200 for a plausible-but-unknown
// station, and HTTP 400 for a malformed one — check the error key either way.
internal fun DataGetterResponse.toPredictionsOrThrow(
    httpSuccess: Boolean,
    rawBody: String,
): List<TidePrediction> {
    error?.let { throw TidesStationError(it.message) }
    if (!httpSuccess) {
        throw TidesHttpError("Tides HTTP error: ${rawBody.take(500)}")
    }
    return predictions.orEmpty().mapNotNull { it.toDomainOrNull() }
}

internal fun PredictionDto.toDomainOrNull(): TidePrediction? {
    val heightFt = v.toDoubleOrNull() ?: return null
    val tideType = TideType.fromApi(type) ?: return null
    val time = runCatching { parseStationLocalTime(t) }.getOrNull() ?: return null
    return TidePrediction(time = time, heightFt = heightFt, type = tideType)
}
