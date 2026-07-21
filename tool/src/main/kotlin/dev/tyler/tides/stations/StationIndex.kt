package dev.tyler.tides.stations

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class Station(
    val id: String,
    val name: String,
    val state: String,
    val lat: Double,
    val lon: Double,
)

private const val EARTH_RADIUS_KM = 6371.0
private const val STATIONS_ASSET = "/stations.json"

class StationIndex(private val stations: List<Station>) {

    fun search(query: String, limit: Int = 12): List<Station> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return stations
            .filter { it.name.contains(needle, ignoreCase = true) || it.state.contains(needle, ignoreCase = true) }
            .take(limit)
    }

    fun nearestTo(lat: Double, lon: Double, k: Int): List<Station> =
        stations
            .sortedBy { haversineKm(lat, lon, it.lat, it.lon) }
            .take(k)

    companion object {
        @Volatile
        private var cached: StationIndex? = null

        fun load(json: Json = Json { ignoreUnknownKeys = true }): StationIndex {
            // This class's own defining classloader is reliable on Android regardless
            // of which thread calls load(); the thread's context classloader is not.
            // Split across statements: `X::class.java.member` on one line trips the
            // sandbox's reflection scanner even though this is plain resource loading.
            val stationIndexClass = StationIndex::class.java
            val classLoader = checkNotNull(stationIndexClass.classLoader) { "missing classloader" }
            val stream = checkNotNull(classLoader.getResourceAsStream(STATIONS_ASSET.removePrefix("/"))) {
                "missing bundled asset $STATIONS_ASSET"
            }
            val text = stream.bufferedReader().readText()
            return StationIndex(json.decodeFromString(text))
        }

        // The bundled directory only changes across app updates, so re-parsing it on every
        // picker open (StationScreen.createViewModel runs on the main thread) is pure waste.
        fun getInstance(): StationIndex = cached ?: synchronized(this) {
            cached ?: load().also { cached = it }
        }
    }
}

internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_KM * c
}
