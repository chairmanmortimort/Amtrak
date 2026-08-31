package com.thelightphone.wikipedia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** A single paragraph line within the article body. [isLink] marks lines that exactly match a known related-article title and should render underlined and tappable. */
private data class ParagraphLine(val text: String, val isLink: Boolean)

/** A section block: [header] is null for the intro, otherwise the == Section == title. */
private data class ArticleBlock(val header: String?, val paragraphs: List<ParagraphLine>)

/** Splits a MediaWiki plain-text extract into an intro block (the text before the first section header) and a list of section blocks. Terminal sections (References, Sources, etc.) are dropped entirely, as they are not useful on a small monochrome display. */
private fun parseArticle(
    extract: String,
    linkSet: Set<String>,
): Pair<List<ParagraphLine>, List<ArticleBlock>> {
    val TERMINAL = setOf("references", "sources", "further reading", "external links", "see also", "footnotes")
    val sectionRegex = Regex("^(=+)\\s*(.+?)\\s*=+$")

    val intro = mutableListOf<ParagraphLine>()
    val sections = mutableListOf<ArticleBlock>()
    var currentHeader: String? = null
    var currentParas = mutableListOf<ParagraphLine>()
    var inTerminal = false
    var startedSections = false

    for (raw in extract.lines()) {
        val line = raw.trim()
        if (line.isEmpty()) continue

        val m = sectionRegex.find(line)
        if (m != null) {
            val headerText = m.groupValues[2].trim()
            if (headerText.lowercase() in TERMINAL) {
                inTerminal = true
                continue
            }
            inTerminal = false
            if (startedSections) {
                sections.add(ArticleBlock(currentHeader, currentParas.toList()))
            }
            currentHeader = headerText
            currentParas = mutableListOf()
            startedSections = true
            continue
        }

        if (inTerminal) continue
        val para = ParagraphLine(line, linkSet.contains(line))
        if (startedSections) currentParas.add(para) else intro.add(para)
    }
    if (startedSections) {
        sections.add(ArticleBlock(currentHeader, currentParas.toList()))
    }
    return intro.toList() to sections.toList()
}

@Composable
fun ArticleContent(
    title: String,
    description: String?,
    extract: String,
    thumbnailUrl: String?,
    links: List<String>,
    leadExtract: String? = null,
    isLoading: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
    onRandom: () -> Unit = {},
    showRandomArticle: Boolean = true,
    onLongPressWord: (String) -> Unit = {},
) {
    val listState: LazyListState = rememberLazyListState()
    val linkSet = links.toSet()

    val (intro, sections) = remember(extract, links) {
        if (extract.isBlank()) {
            emptyList<ParagraphLine>() to emptyList<ArticleBlock>()
        } else {
            parseArticle(extract, linkSet)
        }
    }

    val totalItems = 1 + sections.size + if (links.isNotEmpty()) 1 else 0

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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp())
                    .padding(top = 0.5f.gridUnitsAsDp()),
            ) {
                // ----- Intro block (title + description + lead paragraphs) -----
                item {
                    Column {
                        LightText(
                            text = title,
                            variant = LightTextVariant.Heading,
                            align = TextAlign.Center,
                            modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                        )
                        description?.let { desc ->
                            if (desc.isNotBlank()) {
                                LightText(
                                    text = desc,
                                    variant = LightTextVariant.Subheading,
                                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                                )
                            }
                        }
                        if (intro.isEmpty()) {
                            LightText(
                                text = "No article text available.",
                                variant = LightTextVariant.Copy,
                                lighten = true,
                            )
                        } else {
                            intro.forEach { line ->
                                ParagraphView(line, onOpenLink, onLongPressWord)
                            }
                        }
                    }
                }

                // ----- Lead summary card (description + quick summary) -----
                if (description != null || leadExtract != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 0.75f.gridUnitsAsDp()),
                        ) {
                            description?.let { desc ->
                                if (desc.isNotBlank()) {
                                    LightText(
                                        text = desc,
                                        variant = LightTextVariant.Subheading,
                                        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                                    )
                                }
                            }
                            leadExtract?.let { lead ->
                                LightText(
                                    text = lead,
                                    variant = LightTextVariant.Copy,
                                    modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                                )
                            }
                        }
                    }
                }

                // ----- Section blocks -----
                items(sections) { block ->
                    Column(modifier = Modifier.padding(top = 1f.gridUnitsAsDp())) {
                        block.header?.let { h ->
                            LightText(
                                text = h,
                                variant = LightTextVariant.Heading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 0.75f.gridUnitsAsDp()),
                            )
                        }
                        block.paragraphs.forEach { line ->
                            ParagraphView(line, onOpenLink, onLongPressWord)
                        }
                    }
                }

                // ----- Related Articles block -----
                if (links.isNotEmpty()) {
                    item {
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

        // Bottom bar: BACK, (conditional) RANDOM — NO FAST_FORWARD
        LightBottomBar(
            items = buildList {
                add(
                    LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = onBack,
                        contentDescription = "Back",
                    ),
                )
                if (showRandomArticle) {
                    add(
                        LightBarButton.LightIcon(
                            icon = LightIcons.LOOP,
                            onClick = onRandom,
                            contentDescription = "Random article",
                        ),
                    )
                }
            },
        )
    }
}

/** Resolves the word surrounding a character offset in [text]. Used so a long-press targets the specific word, not the whole line. */
private fun wordAtOffset(text: String, offset: Int): String {
    if (text.isEmpty()) return text
    val o = offset.coerceIn(0, text.length - 1)
    var s = o
    while (s > 0 && text[s - 1].isLetterOrDigit()) s--
    var e = o
    while (e < text.length && text[e].isLetterOrDigit()) e++
    return text.substring(s, e).trim()
}

@Composable
private fun ParagraphView(
    line: ParagraphLine,
    onOpenLink: (String) -> Unit,
    onLongPressWord: (String) -> Unit = {},
) {
    // Capture text layout so a long-press can resolve the specific word.
    var layoutResult by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val resolveWord: (Offset) -> Unit = { pos ->
        val off = layoutResult?.getOffsetForPosition(pos) ?: -1
        val word = if (off >= 0) wordAtOffset(line.text, off) else ""
        if (word.isNotBlank()) onLongPressWord(word) else onLongPressWord(line.text)
    }
    val pressModifier = Modifier
        .fillMaxWidth()
        .pointerInput(line.text) { detectTapGestures(onLongPress = resolveWord) }

    if (line.isLink) {
        Box(modifier = pressModifier) {
            LightText(
                text = line.text,
                variant = LightTextVariant.Paragraph,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = { onOpenLink(line.text) })
                    .padding(vertical = 0.5f.gridUnitsAsDp()),
                color = LightThemeTokens.colors.contentSecondary,
                underline = true,
            )
            BasicText(
                text = line.text,
                onTextLayout = { result -> layoutResult = result },
                color = { Color.Transparent },
                modifier = Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp()),
            )
        }
    } else {
        Box(modifier = pressModifier) {
            LightText(
                text = line.text,
                variant = LightTextVariant.Paragraph,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.25f.gridUnitsAsDp()),
            )
            BasicText(
                text = line.text,
                onTextLayout = { result -> layoutResult = result },
                color = { Color.Transparent },
                modifier = Modifier.fillMaxWidth().padding(vertical = 0.25f.gridUnitsAsDp()),
            )
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