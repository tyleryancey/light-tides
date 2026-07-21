package dev.tyler.tides.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

data class SelectedStation(val id: String, val name: String, val state: String)

interface TideMetaStore {
    suspend fun currentStation(): SelectedStation?
    suspend fun setStation(stationId: String, stationName: String, stationState: String)
    suspend fun lastFetchEpochDay(): Long?
    suspend fun setLastFetchEpochDay(epochDay: Long)
}

private object TidePreferenceKeys {
    val STATION_ID = stringPreferencesKey("station_id")
    val STATION_NAME = stringPreferencesKey("station_name")
    val STATION_STATE = stringPreferencesKey("station_state")
    val LAST_FETCH_EPOCH_DAY = longPreferencesKey("last_fetch_epoch_day")
}

class DataStoreTideMetaStore(
    private val dataStore: DataStore<Preferences>,
) : TideMetaStore {

    override suspend fun currentStation(): SelectedStation? {
        val prefs = dataStore.data.first()
        val id = prefs[TidePreferenceKeys.STATION_ID] ?: return null
        val name = prefs[TidePreferenceKeys.STATION_NAME] ?: return null
        val state = prefs[TidePreferenceKeys.STATION_STATE] ?: return null
        return SelectedStation(id, name, state)
    }

    // Load-bearing that the id/name/state write and the LAST_FETCH_EPOCH_DAY clear happen in one
    // atomic `edit` — TideRepository's unsynchronized reads (outside its mutex) rely on never
    // observing the new station id paired with the old, still-valid fetch stamp. Splitting this
    // into two edits would reopen the false-freshness race the repository's mutex guards against.
    override suspend fun setStation(stationId: String, stationName: String, stationState: String) {
        dataStore.edit { prefs ->
            prefs[TidePreferenceKeys.STATION_ID] = stationId
            prefs[TidePreferenceKeys.STATION_NAME] = stationName
            prefs[TidePreferenceKeys.STATION_STATE] = stationState
            prefs.remove(TidePreferenceKeys.LAST_FETCH_EPOCH_DAY)
        }
    }

    override suspend fun lastFetchEpochDay(): Long? =
        dataStore.data.first()[TidePreferenceKeys.LAST_FETCH_EPOCH_DAY]

    override suspend fun setLastFetchEpochDay(epochDay: Long) {
        dataStore.edit { prefs ->
            prefs[TidePreferenceKeys.LAST_FETCH_EPOCH_DAY] = epochDay
        }
    }
}
