package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
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
 * About screen: attribution for the live data source. Opens from the gear icon
 * on the home screen top bar. Layout and attribution follow the Wikipedia Light
 * and Dictionary tools' About pages.
 */
class AboutScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, AmtrakViewModel>(sealedActivity) {

    override val viewModelClass: Class<AmtrakViewModel>
        get() = AmtrakViewModel::class.java

    override fun createViewModel(): AmtrakViewModel =
        AmtrakViewModel(lightContext.dataStore)

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
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                )
                LightScrollView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1f.gridUnitsAsDp()),
                ) {
                    LightText(
                        text = "Train data",
                        variant = LightTextVariant.Subheading,
                    )
                    LightText(
                        text = "Real-time positions, schedules, and station " +
                            "information for Amtrak services across the United States.",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                    )

                    LightText(
                        text = "Data source",
                        variant = LightTextVariant.Subheading,
                        modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = "Fetched live from the Amtraker API " +
                            "(api.amtraker.com). Station and schedule data " +
                            "originates from Amtrak.",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                    )

                    LightText(
                        text = "Tool",
                        variant = LightTextVariant.Subheading,
                        modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = "Built with the Light Phone SDK. An independent, " +
                            "non-commercial tool; not affiliated with Amtrak or " +
                            "the National Railroad Passenger Corporation.",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
                    )

                    Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
                }
            }
        }
    }
}
