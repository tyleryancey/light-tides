package dev.tyler.tides.data

import com.thelightphone.sdk.LightJobResult
import dev.tyler.tides.api.TidesHttpError
import dev.tyler.tides.api.TidesStationError
import kotlin.test.Test
import kotlin.test.assertIs

class TidesJobTest {
    private val station = SelectedStation(id = "9414290", name = "SAN FRANCISCO (Golden Gate)", state = "CA")

    @Test
    fun noStationSelectedIsSuccessNotRetry() {
        assertIs<LightJobResult.Success>(null.toJobResult())
    }

    @Test
    fun freshOrSuccessfullyRefetchedSnapshotIsSuccess() {
        val snapshot = TidesSnapshot(station, predictions = emptyList(), lastFetchEpochDay = 1L, stale = false)
        assertIs<LightJobResult.Success>(snapshot.toJobResult())
    }

    @Test
    fun transientHttpFailureAsksWorkManagerToRetrySoon() {
        val snapshot = TidesSnapshot(
            station, predictions = emptyList(), lastFetchEpochDay = null,
            stale = true, staleCause = TidesHttpError("503"),
        )
        assertIs<LightJobResult.Retry>(snapshot.toJobResult())
    }

    @Test
    fun unclassifiedFailureAlsoRetries() {
        // e.g. a raw IOException from the network layer, not one of TidesApi's own exception types.
        val snapshot = TidesSnapshot(
            station, predictions = emptyList(), lastFetchEpochDay = null,
            stale = true, staleCause = RuntimeException("no route to host"),
        )
        assertIs<LightJobResult.Retry>(snapshot.toJobResult())
    }

    @Test
    fun permanentStationErrorSkipsToTheNextDailyCycleInsteadOfRetryingForever() {
        val snapshot = TidesSnapshot(
            station, predictions = emptyList(), lastFetchEpochDay = null,
            stale = true, staleCause = TidesStationError("station is not valid"),
        )
        assertIs<LightJobResult.Error>(snapshot.toJobResult())
    }
}
