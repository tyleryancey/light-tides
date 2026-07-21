package dev.tyler.tides.ui

import com.thelightphone.sdk.LightViewModel
import dev.tyler.tides.data.SelectedStation
import dev.tyler.tides.stations.Station
import dev.tyler.tides.stations.StationIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class StationScreenMode {
    data object Search : StationScreenMode()
    data class Results(val query: String, val stations: List<Station>) : StationScreenMode()
}

data class StationUiState(
    val mode: StationScreenMode = StationScreenMode.Search,
    val canCancel: Boolean,
)

class StationViewModel(
    private val stationIndex: StationIndex,
    canCancel: Boolean,
) : LightViewModel<SelectedStation?>() {
    private val _uiState = MutableStateFlow(StationUiState(canCancel = canCancel))
    val uiState: StateFlow<StationUiState> = _uiState.asStateFlow()

    fun submitQuery(query: CharSequence) {
        val trimmed = query.toString()
        _uiState.update { it.copy(mode = StationScreenMode.Results(trimmed, stationIndex.search(trimmed))) }
    }

    fun backToSearch() {
        _uiState.update { it.copy(mode = StationScreenMode.Search) }
    }
}
