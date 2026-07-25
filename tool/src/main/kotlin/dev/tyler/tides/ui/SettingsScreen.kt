package dev.tyler.tides.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.tyler.tides.data.DataStoreUnitsPreferenceStore
import dev.tyler.tides.data.TideDatabase
import dev.tyler.tides.data.TideRepository
import dev.tyler.tides.data.TideUnit

class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    private val repository = TideRepository.getInstance(dataStore = lightContext.dataStore) {
        lightContext.buildDatabase(TideDatabase::class.java, TideDatabase.DATABASE_NAME)
    }

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel(): SettingsViewModel =
        SettingsViewModel(repository, DataStoreUnitsPreferenceStore(lightContext.dataStore))

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text("Settings"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    SettingRow(
                        label = "Units",
                        value = if (state.units == TideUnit.FEET) "Feet" else "Meters",
                        onClick = viewModel::toggleUnits,
                    )
                    SettingRow(
                        label = "Station",
                        value = state.stationName,
                        onClick = {
                            navigateTo(screenFactory = { StationScreen(it, canCancel = true) }) { picked ->
                                picked?.let(viewModel::onStationPicked)
                            }
                        },
                    )
                }

                SourceFooter()
            }
        }
    }
}

// Mirrors the weather example's attribution footer slot. NOAA data is public
// domain — no attribution required — but saying where predictions come from
// (and that coverage is US stations) belongs in the product, not just the README.
@Composable
private fun SourceFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4f.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = "Tide predictions provided by NOAA (US stations).",
            variant = LightTextVariant.Fine,
            lighten = true,
            align = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(text = label, variant = LightTextVariant.Detail, lighten = true)
        LightText(text = value, variant = LightTextVariant.Heading)
    }
}
