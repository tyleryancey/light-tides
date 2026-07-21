package dev.tyler.tides.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

enum class TideUnit {
    FEET,
    METERS,
    ;

    fun toggle(): TideUnit = if (this == FEET) METERS else FEET
}

interface UnitsPreferenceStore {
    suspend fun unit(): TideUnit
    suspend fun setUnit(unit: TideUnit)
}

private val UNIT_KEY = stringPreferencesKey("tide_unit")

class DataStoreUnitsPreferenceStore(
    private val dataStore: DataStore<Preferences>,
) : UnitsPreferenceStore {
    override suspend fun unit(): TideUnit =
        when (dataStore.data.first()[UNIT_KEY]) {
            TideUnit.METERS.name -> TideUnit.METERS
            else -> TideUnit.FEET
        }

    override suspend fun setUnit(unit: TideUnit) {
        dataStore.edit { prefs -> prefs[UNIT_KEY] = unit.name }
    }
}
