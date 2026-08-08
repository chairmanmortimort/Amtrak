package com.thelightphone.wikipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@Composable
fun ArticleContent(
    title: String,
    description: String?,
    extract: String,
    thumbnailUrl: String?,
    links: List<String>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text(title),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = onOpenSettings,
                contentDescription = "About",
            ),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "Loading article…",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp())
                    .padding(top = 0.5f.gridUnitsAsDp())
                    .verticalScroll(scrollState),
            ) {
                Column {
                    // Article title (large, for readability on monochrome display)
                    LightText(
                        text = title,
                        variant = LightTextVariant.Heading,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                    )

                    // Description line (if available)
                    description?.let { desc ->
                        if (desc.isNotBlank()) {
                            LightText(
                                text = desc,
                                variant = LightTextVariant.Subheading,
                                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                            )
                        }
                    }

                    // Article content — parse the plain text extract into sections
                    ArticleBody(
                        extract = extract,
                        links = links,
                        onOpenLink = onOpenLink,
                    )

                    // Links section
                    if (links.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp())) {
                            LightText(
                                text = "Related Articles",
                                variant = LightTextVariant.Subheading,
                                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                            )
                            links.take(15).forEach { linkTitle ->
                                LightText(
                                    text = linkTitle,
                                    variant = LightTextVariant.Copy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable(onClick = { onOpenLink(linkTitle) })
                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                    color = LightThemeTokens.colors.contentSecondary,
                                    underline = true,
                                )
                            }
                            if (links.size > 15) {
                                LightText(
                                    text = "...and ${links.size - 15} more",
                                    variant = LightTextVariant.Fine,
                                    lighten = true,
                                    modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp()),
                                )
                            }
                        }
                    }
                }
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = onBack,
                    contentDescription = "Back",
                ),
            ),
        )
    }
}

/**
 * Renders Wikipedia plain-text extract, preserving section structure.
 *
 * The MediaWiki Action API with explaintext=1 produces text like:
 *   Intro paragraph text.
 *   == Section ==
 *   Paragraph under section.
 *   === Subsection ===
 *   More text.
 *
 * Section headers (==, ===) are bolded for visual hierarchy.
 * Additionally, any line that exactly matches a known article link title
 * (from the links list) is rendered with an underline in secondary color
 * and made tappable via lightClickable, enabling in-article hyperlinks.
 */
@Composable
private fun ArticleBody(
    extract: String,
    links: List<String>,
    onOpenLink: (String) -> Unit,
) {
    if (extract.isBlank()) {
        LightText(
            text = "No article text available.",
            variant = LightTextVariant.Copy,
            lighten = true,
        )
        return
    }

    val linkSet = links.toSet()
    val lines = extract.lines()

    Column {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                return@forEach
            }

            // Detect section headers (e.g. "== History ==" / === Subsection ===)
            val sectionMatch = Regex("^(=+)\\s*(.+?)\\s*=+$").find(line)
            if (sectionMatch != null) {
                val headerText = sectionMatch.groupValues[2].trim()
                // Stop rendering at terminal sections — these are not useful on a small
                // monochrome display: References, Sources, Further reading, External links
                val terminalSections = setOf(
                    "References", "Sources", "Further reading",
                    "External links", "See also", "Footnotes"
                )
                if (headerText.lowercase() in terminalSections.map { it.lowercase() }) {
                    return@forEach
                }
                LightText(
                    text = headerText,
                    variant = LightTextVariant.Heading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
                )
                return@forEach
            }

            // Check if this line is a known article link — make it tappable + underlined
            if (linkSet.contains(line)) {
                LightText(
                    text = line,
                    variant = LightTextVariant.Paragraph,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onOpenLink(line) })
                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                    color = LightThemeTokens.colors.contentSecondary,
                    underline = true,
                )
            } else {
                LightText(
                    text = line,
                    variant = LightTextVariant.Paragraph,
                    modifier = Modifier.padding(vertical = 0.25f.gridUnitsAsDp()),
                )
            }
        }
    }
}

@Composable
fun AboutContent(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("About"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(2f.gridUnitsAsDp())
                .verticalScroll(rememberScrollState()),
        ) {
            Column {
                LightText(
                    text = "Wikipedia Tool",
                    variant = LightTextVariant.Heading,
                )
                LightText(
                    text = "Uses the Wikipedia REST API and MediaWiki Action API.",
                    variant = LightTextVariant.Detail,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
                LightText(
                    text = "Content is licensed under CC BY-SA 4.0.",
                    variant = LightTextVariant.Detail,
                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                )
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = onBack,
                    contentDescription = "Back",
                ),
            ),
        )
    }
}

@Composable
private fun Spacer(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5f.gridUnitsAsDp())
            .background(com.thelightphone.sdk.ui.LightThemeTokens.colors.background),
    )
}
