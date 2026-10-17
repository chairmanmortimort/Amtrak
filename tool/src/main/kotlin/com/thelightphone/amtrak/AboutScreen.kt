package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBarButton
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

/**
 * Attribution / About screen. Opens from the gear icon on every top bar.
 *
 * Train status data is served by amtraker.com — a community-maintained, third-party
 * API with no affiliation to Amtrak. No account or credentials are stored or sent.
 */
class AboutScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text("About"),
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                        contentDescription = "Back",
                    ),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2f.gridUnitsAsDp()),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LightText(
                            text = "Amtrak",
                            variant = LightTextVariant.Heading,
                        )

                        LightText(
                            text = "Train status for the Light Phone III.",
                            variant = LightTextVariant.Detail,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        )

                        LightText(
                            text = "Data source",
                            variant = LightTextVariant.Subheading,
                            modifier = Modifier.padding(top = 2f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "Live train data comes from amtraker.com, a community API. " +
                                "It is not affiliated with or endorsed by Amtrak and has no " +
                                "uptime guarantee — if the API is unavailable, live status " +
                                "cannot be shown.",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                        )

                        LightText(
                            text = "Notices",
                            variant = LightTextVariant.Subheading,
                            modifier = Modifier.padding(top = 2f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "Amtrak and all train names are trademarks of Amtrak. " +
                                "No account credentials are stored on the device.",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                        )

                        LightText(
                            text = "Tool",
                            variant = LightTextVariant.Subheading,
                            modifier = Modifier.padding(top = 2f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "Built with the Light Phone SDK. © 2026 thelightphone community.",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                        )
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack(null) },
                            contentDescription = "Back",
                        ),
                    ),
                )
            }
        }
    }
}
