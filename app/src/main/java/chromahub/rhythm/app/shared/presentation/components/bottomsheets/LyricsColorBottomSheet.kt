/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricColorSwatches
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricsColorStyle
import chromahub.rhythm.app.shared.presentation.components.lyrics.rememberLyricRainbowBrush
import chromahub.rhythm.app.shared.presentation.components.lyrics.resolveLyricColors
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import androidx.compose.material3.rememberBottomSheetState

@Composable
fun lyricColorStyleName(style: String): String = when (style) {
    LyricsColorStyle.RAINBOW -> stringResource(R.string.lyrics_color_rainbow)
    LyricsColorStyle.OCEAN -> stringResource(R.string.lyrics_color_ocean)
    LyricsColorStyle.SUNSET -> stringResource(R.string.lyrics_color_sunset)
    LyricsColorStyle.NEON -> stringResource(R.string.lyrics_color_neon)
    LyricsColorStyle.MONO -> stringResource(R.string.lyrics_color_mono)
    LyricsColorStyle.CUSTOM -> stringResource(R.string.lyrics_color_custom)
    else -> stringResource(R.string.lyrics_color_theme)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsColorBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colorStyle by appSettings.lyricColorStyle.collectAsState()
    val textColorArgb by appSettings.lyricTextColor.collectAsState()
    val activeColorArgb by appSettings.lyricActiveColor.collectAsState()
    val resolved = resolveLyricColors(colorStyle, textColorArgb, activeColorArgb)
    val rainbowBrush = rememberLyricRainbowBrush()

    val styleIds = LyricsColorStyle.presets
    val optionTitles = mapOf(
        LyricsColorStyle.THEME to stringResource(R.string.lyrics_color_theme),
        LyricsColorStyle.RAINBOW to stringResource(R.string.lyrics_color_rainbow),
        LyricsColorStyle.OCEAN to stringResource(R.string.lyrics_color_ocean),
        LyricsColorStyle.SUNSET to stringResource(R.string.lyrics_color_sunset),
        LyricsColorStyle.NEON to stringResource(R.string.lyrics_color_neon),
        LyricsColorStyle.MONO to stringResource(R.string.lyrics_color_mono),
        LyricsColorStyle.CUSTOM to stringResource(R.string.lyrics_color_custom)
    )
    val optionDescriptions = mapOf(
        LyricsColorStyle.THEME to stringResource(R.string.lyrics_color_theme_desc),
        LyricsColorStyle.RAINBOW to stringResource(R.string.lyrics_color_rainbow_desc),
        LyricsColorStyle.CUSTOM to stringResource(R.string.lyrics_color_custom_desc)
    )

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.settings_lyrics_colors),
            subtitle = stringResource(R.string.settings_lyrics_colors_desc),
            visible = true
        )

        val scrollState = rememberScrollState()
        AdaptiveSheetScrollContainer(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth()
        ) { endPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live preview
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lyrics_color_preview_normal),
                            style = MaterialTheme.typography.titleMedium,
                            color = resolved.text ?: MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.lyrics_color_preview_active),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                brush = if (resolved.rainbow) rainbowBrush else null
                            ),
                            color = if (resolved.rainbow) Color.Unspecified
                            else resolved.active ?: MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                styleIds.forEachIndexed { index, styleId ->
                    val isSelected = colorStyle == styleId
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setLyricColorStyle(styleId)
                            if (styleId != LyricsColorStyle.CUSTOM) {
                                onDismiss()
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = groupedBottomSheetItemShape(index, styleIds.size),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PresetSwatch(styleId = styleId, rainbowBrush = rainbowBrush)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = optionTitles[styleId] ?: styleId,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                val description = optionDescriptions[styleId].orEmpty()
                                if (description.isNotBlank()) {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (colorStyle == LyricsColorStyle.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.lyrics_color_text),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    ColorSwatchRow(
                        selectedArgb = textColorArgb,
                        onSelect = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setLyricTextColor(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.lyrics_color_active_line),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    ColorSwatchRow(
                        selectedArgb = activeColorArgb,
                        onSelect = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setLyricActiveColor(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetSwatch(styleId: String, rainbowBrush: Brush) {
    val colors = when (styleId) {
        LyricsColorStyle.OCEAN -> listOf(Color(0xFFB3E5FC), Color(0xFF29B6F6))
        LyricsColorStyle.SUNSET -> listOf(Color(0xFFFFE0B2), Color(0xFFFF7043))
        LyricsColorStyle.NEON -> listOf(Color(0xFFE1BEE7), Color(0xFFE040FB))
        LyricsColorStyle.MONO -> listOf(Color(0xFFBDBDBD), Color(0xFFFFFFFF))
        else -> listOf(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                when (styleId) {
                    LyricsColorStyle.RAINBOW -> rainbowBrush
                    else -> Brush.linearGradient(colors)
                }
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
    )
}

@Composable
private fun ColorSwatchRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(LyricColorSwatches) { color ->
            val argb = color.toArgb()
            val isSelected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(if (isSelected) 42.dp else 34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(argb) }
            )
        }
    }
}
