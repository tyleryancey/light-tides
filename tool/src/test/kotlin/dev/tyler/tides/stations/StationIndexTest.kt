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
    fun searchRanksExactStateMatchesAboveIncidentalNameSubstrings() {
        // "or" is a substring of every "Harbor" — an Oregonian typing their state
        // code must see Oregon stations, not Pacific-island harbors.
        val fixture = StationIndex(
            listOf(
                Station(id = "1", name = "Apra Harbor", state = "", lat = 0.0, lon = 0.0),
                Station(id = "2", name = "South Beach", state = "OR", lat = 0.0, lon = 0.0),
                Station(id = "3", name = "Newport Bay Entrance", state = "OR", lat = 0.0, lon = 0.0),
            ),
        )

        assertEquals(listOf("2", "3", "1"), fixture.search("OR").map { it.id })
    }

    @Test
    fun searchRanksNamePrefixMatchesAboveMidNameSubstrings() {
        val fixture = StationIndex(
            listOf(
                Station(id = "1", name = "Hyder, Portland Canal", state = "AK", lat = 0.0, lon = 0.0),
                Station(id = "2", name = "PORTLAND", state = "ME", lat = 0.0, lon = 0.0),
                Station(id = "3", name = "Portland Head Light", state = "ME", lat = 0.0, lon = 0.0),
            ),
        )

        assertEquals(listOf("2", "3", "1"), fixture.search("Portland").map { it.id })
    }

    @Test
    fun searchForOregonByStateCodeFillsEverySlotWithOregonAgainstTheRealDirectory() {
        // The directory has 47 OR stations — more than the 12-result cap — so with
        // exact-state ranking every returned row must be an Oregon station.
        val results = index.search("OR")
        assertEquals(12, results.size)
        assertTrue(results.all { it.state == "OR" })
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

    @Test
    fun bundledDirectoryPatchesTerritoryStatesAndDropsForeignMislabels() {
        // NOAA leaves state blank on a handful of genuine US-territory stations and
        // stamps "AS" on foreign Apia (independent Samoa). gen-stations.sh patches
        // the former by id and excludes the latter — this guards both across regens.
        val byId = index.nearestTo(0.0, 0.0, k = Int.MAX_VALUE).associateBy { it.id }
        assertEquals("GU", byId["1630000"]?.state, "Apra Harbor, Guam")
        assertEquals("AS", byId["1770000"]?.state, "Pago Pago Harbor, Tutuila")
        assertEquals("MP", byId["TPT2623"]?.state, "Saipan Harbor")
        assertEquals("WA", byId["TWC1131"]?.state, "Stanwood, Stillaguamish River")
        assertEquals("PW", byId["1841275"]?.state, "Malakal Harbor, Palau (COFA)")
        assertEquals(null, byId["1778000"], "Apia is foreign — must be excluded")
    }

    @Test
    fun bundledDirectoryCarriesAStateForEveryStation() {
        // v1 is US-coastal only: gen-stations.sh drops NOAA's blank-state foreign
        // holdovers (Papeete, B.C., Galapagos, outlying atolls) at generation time,
        // so every bundled station has a state/territory code. Guards regeneration.
        val all = index.nearestTo(0.0, 0.0, k = Int.MAX_VALUE)
        assertTrue(all.size > 3000, "directory unexpectedly small: ${all.size}")
        assertTrue(all.all { it.state.isNotBlank() }, "blank-state stations present")
    }

    @Test
    fun bundledDirectoryNamesKeepFirstCommaTruncationSafe() {
        // shortStationName truncates at the first comma; a comma inside parentheses
        // would make that split mid-parenthetical. Holds today — guards regens.
        val all = index.nearestTo(0.0, 0.0, k = Int.MAX_VALUE)
        assertTrue(all.none { it.name.matches(Regex(".*\\([^)]*,.*")) })
    }
}
