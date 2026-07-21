package dev.tyler.tides.stations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationIndexTest {
    private val index = StationIndex.load()

    // Ground truth for each point independently cross-checked against the
    // generated stations.json with the same haversine formula in Python.
    private val handCheckedNearest = listOf(
        Triple(37.7749, -122.4194, "9414317"), // San Francisco, CA
        Triple(47.6062, -122.3321, "9447130"), // Seattle, WA
        Triple(25.7617, -80.1918, "8723165"), // Miami, FL
        Triple(42.3601, -71.0589, "8443970"), // Boston, MA
        Triple(21.3069, -157.8583, "1612340"), // Honolulu, HI
    )

    @Test
    fun nearestToMatchesHandCheckedStationsForFiveCoastalPoints() {
        handCheckedNearest.forEach { (lat, lon, expectedId) ->
            val nearest = index.nearestTo(lat, lon, k = 1).single()
            assertEquals(expectedId, nearest.id, "nearest station to ($lat, $lon)")
        }
    }

    @Test
    fun nearestToReturnsKResultsSortedByDistance() {
        val results = index.nearestTo(37.7749, -122.4194, k = 3)
        assertEquals(3, results.size)
        val distances = results.map { haversineKm(37.7749, -122.4194, it.lat, it.lon) }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun searchMatchesNameCaseInsensitivelyAgainstTheRealDirectory() {
        val byName = index.search("san francisco")
        assertTrue(byName.isNotEmpty())
        assertTrue(byName.all { it.name.contains("san francisco", ignoreCase = true) })
    }

    @Test
    fun searchMatchesByStateEvenWhenNameDoesNotMatch() {
        // Small controlled fixture — the real directory's file order would make
        // "all results are HI" pass by luck rather than by the OR-over-state behavior.
        val fixture = StationIndex(
            listOf(
                Station(id = "1", name = "Honolulu Harbor", state = "HI", lat = 0.0, lon = 0.0),
                Station(id = "2", name = "Nowhere Point", state = "hi", lat = 0.0, lon = 0.0),
                Station(id = "3", name = "Elsewhere", state = "CA", lat = 0.0, lon = 0.0),
            ),
        )

        val results = fixture.search("HI")

        assertEquals(setOf("1", "2"), results.map { it.id }.toSet())
    }

    @Test
    fun searchIsCappedAtTheRequestedLimit() {
        val results = index.search("Bay", limit = 5)
        assertTrue(results.size <= 5)
    }

    @Test
    fun searchOnBlankQueryReturnsNoResults() {
        assertEquals(emptyList(), index.search("   "))
    }

    @Test
    fun loadsTheFullBundledDirectory() {
        assertTrue(index.nearestTo(0.0, 0.0, k = 1).isNotEmpty())
    }
}
