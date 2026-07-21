package dev.tyler.tides.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.tides.api.TidePrediction
import dev.tyler.tides.api.TideType
import dev.tyler.tides.data.DataStoreUnitsPreferenceStore
import dev.tyler.tides.data.TideDatabase
import dev.tyler.tides.data.TideRepository
import dev.tyler.tides.data.TideUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    private val repository = TideRepository.getInstance(dataStore = lightContext.dataStore) {
        lightContext.buildDatabase(TideDatabase::class.java, TideDatabase.DATABASE_NAME)
    }

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel =
        HomeViewModel(repository, DataStoreUnitsPreferenceStore(lightContext.dataStore))

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LaunchedEffect(state.mode) {
            // Re-read the ViewModel's own flow rather than the `state` collected above: the
            // collector and this effect can each observe the snapshot at slightly different
            // points, and isProcessingPick is only guaranteed consistent with the mode that
            // wrote it, not with whatever `state` happened to snapshot first.
            val currentMode = viewModel.uiState.value.mode
            if (currentMode is HomeScreenMode.NeedsStation && !viewModel.isProcessingPick) {
                navigateTo(screenFactory = { StationScreen(it, canCancel = false) }) { picked ->
                    viewModel.onStationPicked(picked)
                }
            }
        }

        LightTheme(colors = themeColors) {
            when (val mode = state.mode) {
                is HomeScreenMode.Loading, is HomeScreenMode.NeedsStation -> LoadingContent()
                is HomeScreenMode.ErrorState -> ErrorContent(message = mode.message, onRetry = viewModel::reload)
                is HomeScreenMode.Content -> TidesContent(
                    mode = mode,
                    units = state.units,
                    onOpenSettings = { navigateTo(screenFactory = { SettingsScreen(it) }) },
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        LightText(text = "Loading…", variant = LightTextVariant.Copy)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
        LightTopBar(
            center = LightTopBarCenter.Text("Tides"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = message, variant = LightTextVariant.Copy, align = TextAlign.Center)
        }
        LightBottomBar(
            items = listOf(
                null,
                LightBarButton.LightIcon(icon = LightIcons.REFRESH, onClick = onRetry, contentDescription = "Retry"),
                null,
            ),
        )
    }
}

@Composable
private fun TidesContent(
    mode: HomeScreenMode.Content,
    units: TideUnit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
        LightTopBar(
            center = LightTopBarCenter.Text(mode.stationName),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            if (mode.next != null) {
                LightText(
                    text = "Next: ${formatTideLine(mode.next, units)}",
                    variant = LightTextVariant.Title,
                    modifier = Modifier.padding(bottom = (if (mode.countdown != null) 0f else 0.75f).gridUnitsAsDp()),
                )
                if (mode.countdown != null) {
                    LightText(
                        text = mode.countdown,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp()),
                    )
                }
            } else {
                LightText(
                    text = "No more tides in the next 7 days.",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp()),
                )
            }

            if (mode.todaysRemaining.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp())) {
                    LightText(
                        text = "Today",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )
                    mode.todaysRemaining.forEach { TideRow(it, units) }
                }
            }

            Column {
                LightText(
                    text = "This Week",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )
                mode.week.forEach { day ->
                    Column(modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp())) {
                        LightText(
                            text = day.date.format(DAY_TITLE_FORMAT),
                            variant = LightTextVariant.Copy,
                            modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                        )
                        day.events.forEach { TideRow(it, units) }
                    }
                }
            }

            mode.updatedRelative?.let { stamp ->
                LightText(
                    text = stamp,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp()),
                )
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = onOpenSettings,
                    contentDescription = "Settings",
                ),
                null,
                null,
            ),
        )
    }
}

@Composable
private fun TideRow(prediction: TidePrediction, units: TideUnit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.15f.gridUnitsAsDp())) {
        LightIcon(
            icon = if (prediction.type == TideType.HIGH) LightIcons.UP else LightIcons.DOWN,
            modifier = Modifier.padding(end = 0.5f.gridUnitsAsDp()),
            contentDescription = if (prediction.type == TideType.HIGH) "High" else "Low",
        )
        LightText(text = formatHeightTimeLine(prediction, units), variant = LightTextVariant.Copy)
    }
}

private val DAY_TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
