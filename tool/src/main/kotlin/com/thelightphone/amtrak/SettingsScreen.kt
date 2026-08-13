package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
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
import com.thelightphone.sdk.ui.lightClickable

class SettingsScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, AmtrakViewModel>(sealedActivity) {

    override val viewModelClass: Class<AmtrakViewModel>
        get() = AmtrakViewModel::class.java

    override fun createViewModel(): AmtrakViewModel =
        AmtrakViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val settings by viewModel.settingsState.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()

        LightTheme(colors = themeColors) {
            Column(modifier = Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Settings"),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    LightScrollView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        ToggleSettingRow(
                            label = "Haptic feedback",
                            isEnabled = settings.hapticsEnabled,
                            onToggle = { viewModel.toggleHaptics() },
                        )
                        ToggleSettingRow(
                            label = "Color inverted",
                            isEnabled = settings.colorInverted,
                            onToggle = { viewModel.toggleColorInverted() },
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                        LightText(
                            text = "Data & Attribution",
                            variant = LightTextVariant.Subheading,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "Train data provided by Amtraker API",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                        LightText(
                            text = "https://api.amtraker.com/v2",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                        Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                        LightText(
                            text = "Station and schedule data sourced from Amtrak.",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                    }
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                            contentDescription = "Back",
                        ),
                    ),
                )
            }
            errorModal?.let { message ->
                LightFullscreenModal(
                    message = message,
                    onClose = { viewModel.dismissError() },
                )
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    label: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val toggleColor = if (isEnabled) colors.content else colors.contentSecondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onToggle)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Detail,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = if (isEnabled) "On" else "Off",
                variant = LightTextVariant.Heading,
            )
            Spacer(modifier = Modifier.weight(1f))
            // Toggle switch visualization — a simple colored bar that flips based on state
            Box(
                modifier = Modifier
                    .width(6f.gridUnitsAsDp())
                    .height(1.5f.gridUnitsAsDp())
                    .background(
                        if (isEnabled) toggleColor else colors.contentSecondary.copy(alpha = 0.5f),
                    ),
            )
        }
    }
}
