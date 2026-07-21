package dev.tyler.tides.ui

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TidesStationError
import dev.tyler.tides.data.SelectedStation
import dev.tyler.tides.data.TideRepository
import dev.tyler.tides.data.TideUnit
import dev.tyler.tides.data.UnitsPreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

sealed class HomeScreenMode {
    data object Loading : HomeScreenMode()
    data object NeedsStation : HomeScreenMode()

    data class Content(
        val stationId: String,
        val stationName: String,
        val next: TidePrediction?,
        val countdown: String?,
        val todaysRemaining: List<TidePrediction>,
        val week: List<DayTides>,
        val updatedRelative: String?,
    ) : HomeScreenMode()

    data class ErrorState(val message: String) : HomeScreenMode()
}

data class HomeUiState(
    val mode: HomeScreenMode = HomeScreenMode.Loading,
    val units: TideUnit = TideUnit.FEET,
)

internal const val OFFLINE_EMPTY_MESSAGE = "Tides requires a network connection the first " +
    "time you view a station. Please insert a data sim or connect to wi-fi."
internal const val STATION_ERROR_MESSAGE = "This station isn't reporting predictions right now. " +
    "Try choosing another one."

class HomeViewModel(
    private val repository: TideRepository,
    private val unitsStore: UnitsPreferenceStore,
) : LightViewModel<Unit>() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // onScreenShow and the StationScreen result callback can both fire a reload around the
    // same time (returning from the picker re-triggers onScreenShow too); without this, whichever
    // read of the not-yet-persisted station loses the race and clobbers state back to NeedsStation.
    private val reloadMutex = Mutex()

    // LightActivity fully unmounts/remounts each screen's Content() on every navigation, so
    // returning from the picker refires LaunchedEffect(state.mode) against a state snapshot that
    // is still (transiently) NeedsStation — before onStationPicked's async write has landed. This
    // flag is set synchronously, in the same call stack as the result delivery, so the very next
    // recomposition (always deferred to a later frame) sees it and skips re-opening the picker.
    @Volatile
    var isProcessingPick: Boolean = false
        private set

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        reload()
    }

    // Units live in Settings' own screen/ViewModel, so Home only learns about a change by
    // re-reading the preference here — which onScreenShow already runs on every return to Home.
    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            reloadMutex.withLock {
                loadAndUpdate(units = unitsStore.unit())
            }
        }
    }

    fun onStationPicked(station: SelectedStation?) {
        isProcessingPick = true
        if (station == null) {
            isProcessingPick = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reloadMutex.withLock {
                    repository.selectStation(station.id, station.name, station.state)
                    loadAndUpdate()
                }
            } finally {
                isProcessingPick = false
            }
        }
    }

    // Settings can change the selected station out from under Home; without this check, Home
    // keeps showing the previous station's tides — with no loading indication — for the entire
    // fetch of the new one. Flips to Loading first whenever the persisted station has moved on
    // from whatever is currently rendered.
    private suspend fun loadAndUpdate(units: TideUnit? = null) {
        val today = LocalDate.now()
        val persistedStationId = repository.currentStation()?.id
        val renderedStationId = (_uiState.value.mode as? HomeScreenMode.Content)?.stationId
        if (persistedStationId != null && persistedStationId != renderedStationId) {
            _uiState.update { it.copy(mode = HomeScreenMode.Loading) }
        }
        val snapshot = repository.loadTides(today)
        _uiState.update { current ->
            current.copy(mode = snapshot.toScreenMode(today), units = units ?: current.units)
        }
    }
}

internal fun dev.tyler.tides.data.TidesSnapshot?.toScreenMode(today: LocalDate): HomeScreenMode {
    val snapshot = this ?: return HomeScreenMode.NeedsStation
    if (snapshot.predictions.isEmpty() && snapshot.stale) {
        // A permanent NOAA error (bad station/query) reads nothing like "no network" — telling
        // an online user to check their SIM would be a false diagnosis with no way to correct it.
        val message = if (snapshot.staleCause is TidesStationError) STATION_ERROR_MESSAGE else OFFLINE_EMPTY_MESSAGE
        return HomeScreenMode.ErrorState(message)
    }

    val now = LocalDateTime.now()
    val next = nextTide(snapshot.predictions, now)
    val plausibleZone = isPlausiblePhoneZone(snapshot.station.state)
    return HomeScreenMode.Content(
        stationId = snapshot.station.id,
        stationName = snapshot.station.name,
        next = next,
        countdown = next?.takeIf { plausibleZone }?.let { countdownText(it, now) },
        todaysRemaining = todaysRemaining(snapshot.predictions, today, now),
        week = groupByDay(thisWeek(snapshot.predictions, today)),
        updatedRelative = if (snapshot.stale) relativeUpdatedText(snapshot.lastFetchEpochDay, today) else null,
    )
}
