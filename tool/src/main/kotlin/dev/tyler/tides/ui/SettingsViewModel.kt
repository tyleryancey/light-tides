package dev.tyler.tides.ui

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import dev.tyler.tides.data.SelectedStation
import dev.tyler.tides.data.TideRepository
import dev.tyler.tides.data.TideUnit
import dev.tyler.tides.data.UnitsPreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val stationName: String = "",
    val units: TideUnit = TideUnit.FEET,
)

class SettingsViewModel(
    private val repository: TideRepository,
    private val unitsStore: UnitsPreferenceStore,
) : LightViewModel<Unit>() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val station = repository.currentStation()
        val units = unitsStore.unit()
        _uiState.update { it.copy(stationName = station?.name.orEmpty(), units = units) }
    }

    fun toggleUnits() {
        viewModelScope.launch {
            val next = _uiState.value.units.toggle()
            unitsStore.setUnit(next)
            _uiState.update { it.copy(units = next) }
        }
    }

    fun onStationPicked(station: SelectedStation) {
        viewModelScope.launch {
            repository.selectStation(station.id, station.name, station.state)
            refresh()
        }
    }
}
