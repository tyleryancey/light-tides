package dev.tyler.tides.data

class FakeTideMetaStore : TideMetaStore {
    private var station: SelectedStation? = null
    private var lastFetch: Long? = null

    override suspend fun currentStation(): SelectedStation? = station

    override suspend fun setStation(stationId: String, stationName: String, stationState: String) {
        station = SelectedStation(stationId, stationName, stationState)
        lastFetch = null
    }

    override suspend fun lastFetchEpochDay(): Long? = lastFetch

    override suspend fun setLastFetchEpochDay(epochDay: Long) {
        lastFetch = epochDay
    }
}
