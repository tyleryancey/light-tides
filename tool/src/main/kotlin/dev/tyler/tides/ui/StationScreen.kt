package dev.tyler.tides.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.tyler.tides.data.SelectedStation
import dev.tyler.tides.stations.Station
import dev.tyler.tides.stations.StationIndex

class StationScreen(
    sealedActivity: SealedLightActivity,
    private val canCancel: Boolean,
) : LightScreen<SelectedStation?, StationViewModel>(sealedActivity) {

    override val viewModelClass: Class<StationViewModel>
        get() = StationViewModel::class.java

    override fun createViewModel(): StationViewModel = StationViewModel(StationIndex.getInstance(), canCancel)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val textFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            when (val mode = state.mode) {
                is StationScreenMode.Search -> LightTextInputEditor(
                    title = "Find Station",
                    editorKey = mode,
                    state = textFieldState,
                    keyboardOptionsFlow = keyboardOptionsFlow,
                    onSubmit = viewModel::submitQuery,
                    onBack = { goBack(null) },
                    submitIcon = LightIcons.SEARCH,
                    showBackButton = state.canCancel,
                    modifier = Modifier.fillMaxSize(),
                )

                is StationScreenMode.Results -> ResultsContent(
                    query = mode.query,
                    results = mode.stations,
                    onSelect = { station -> goBack(SelectedStation(station.id, station.name, station.state)) },
                    onBack = viewModel::backToSearch,
                )
            }
        }
    }
}

@Composable
private fun ResultsContent(
    query: String,
    results: List<Station>,
    onSelect: (Station) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
            ),
            center = LightTopBarCenter.Text("Results"),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        if (results.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "No stations match \"$query\".",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                )
            }
        } else {
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
            ) {
                results.forEach { station ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { onSelect(station) })
                            .padding(vertical = 0.75f.gridUnitsAsDp()),
                    ) {
                        LightText(text = station.name, variant = LightTextVariant.Heading)
                        LightText(text = station.state, variant = LightTextVariant.Detail, lighten = true)
                    }
                }
            }
        }
    }
}
