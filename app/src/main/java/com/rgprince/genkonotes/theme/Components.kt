package com.rgprince.genkonotes.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgprince.genkonotes.theme.PapierTheme

/**
 * Paper-press treatment: on press the face sinks 2dp toward its static hard
 * shadow and shrinks to 98% (tween in, spring out). Shared by every button —
 * no ripple, ripple would break Flat Paper. The interaction source is shared
 * with the caller's clickable so press state is exact.
 */
class PapierPress(val modifier: Modifier, val source: MutableInteractionSource)

@Composable
fun rememberPapierPress(enabled: Boolean = true): PapierPress {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val on = pressed && enabled
    val spec = if (on) tween<Dp>(90, easing = EaseOut)
    else spring(stiffness = 400f, dampingRatio = 0.7f)
    val scaleSpec = if (on) tween<Float>(90, easing = EaseOut)
    else spring(stiffness = 400f, dampingRatio = 0.7f)
    val dy by animateDpAsState(if (on) 2.dp else 0.dp, spec)
    val scale by animateFloatAsState(if (on) 0.98f else 1f, scaleSpec)
    val modifier = Modifier.graphicsLayer {
        translationY = dy.toPx()
        scaleX = scale
        scaleY = scale
    }
    return PapierPress(modifier, source)
}

enum class PapierButtonVariant { InkFilled, Accent, Outline, Danger }

@Composable
fun PapierButton(
    text: String,
    modifier: Modifier = Modifier,
    variant: PapierButtonVariant = PapierButtonVariant.InkFilled,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = PapierTheme.colors
    val (bg, fg, border) = when (variant) {
        PapierButtonVariant.InkFilled -> Triple(colors.ink, colors.paper, colors.ink)
        PapierButtonVariant.Accent -> Triple(colors.accent, colors.accentInk, colors.ink)
        PapierButtonVariant.Outline -> Triple(colors.paperRaised, colors.ink, colors.ink)
        PapierButtonVariant.Danger -> Triple(colors.berry, colors.onDanger, colors.ink)
    }
    val shape = RoundedCornerShape(10.dp)
    val press = rememberPapierPress(enabled)
    Box(modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 0.dp, y = 3.dp)
                .clip(shape)
                .background(colors.shadow)
        )
        Box(
            modifier = Modifier
                .then(press.modifier)
                .clip(shape)
                .background(if (enabled) bg else colors.line)
                .border(1.dp, border, shape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    indication = null,
                    interactionSource = press.source,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.text.BasicText(
                text = text,
                style = TextStyle(
                    color = if (enabled) fg else colors.inkFaint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PapierTheme.type.bodyFamily
                )
            )
        }
    }
}

@Composable
fun PapierCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    borderOverride: Color? = null,
    ghost: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = PapierTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val press = rememberPapierPress(onClick != null)
    val edge = borderOverride ?: colors.ink
    Box(modifier = modifier) {
        if (!ghost) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 0.dp, y = 3.dp)
                    .clip(shape)
                    .background(colors.shadow)
            )
        }
        val clickableMod = if (onClick != null) {
            Modifier.clickable(
                role = Role.Button,
                indication = null,
                interactionSource = press.source,
                onClick = onClick
            )
        } else Modifier
        Row(
            modifier = Modifier
                .then(press.modifier)
                .clip(shape)
                .background(tint ?: if (ghost) colors.paperSunken else colors.paperRaised)
                .border(if (selected || borderOverride != null) 2.dp else 1.dp, edge, shape)
                .then(clickableMod)
        ) {
            if (selected) {
                Box(modifier = Modifier.width(4.dp).background(colors.accent))
            }
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun PapierChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dot: Color? = null,
    onClick: () -> Unit
) {
    val colors = PapierTheme.colors
    val shape = RoundedCornerShape(6.dp)
    val press = rememberPapierPress()
    val bg by animateColorAsState(
        if (selected) colors.ink else colors.paperSunken,
        tween(150, easing = EaseOutCubic)
    )
    val fg by animateColorAsState(
        if (selected) colors.paper else colors.ink,
        tween(150, easing = EaseOutCubic)
    )
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(press.modifier)
            .clip(shape)
            .background(bg)
            .border(1.dp, colors.ink, shape)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .clickable(
                role = Role.Button,
                indication = null,
                interactionSource = press.source,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
            Spacer(modifier = Modifier.width(6.dp))
        }
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = PapierTheme.type.bodyFamily
            )
        )
    }
}

/** 1dp full-width divider for rows inside settings cards. */
@Composable
fun PapierDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PapierTheme.colors.line)
    )
}

/** Paper toggle switch: sunken track, raised thumb, 48dp row. No Material Switch. */
@Composable
fun PapierSwitch(
    label: String,
    modifier: Modifier = Modifier,
    description: String = "",
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = PapierTheme.colors
    val press = rememberPapierPress()
    val travel by androidx.compose.animation.core.animateDpAsState(
        if (checked) 20.dp else 0.dp,
        tween(150, easing = EaseOutCubic)
    )
    val track by animateColorAsState(
        if (checked) colors.accent else colors.paperSunken,
        tween(150, easing = EaseOutCubic)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(press.modifier)
            .semantics {
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(
                role = Role.Switch,
                indication = null,
                interactionSource = press.source,
                onClick = onToggle
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(
                text = label,
                style = TextStyle(
                    color = colors.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PapierTheme.type.bodyFamily
                )
            )
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                BodySnippetText(text = description)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(track)
                .border(2.dp, colors.ink, RoundedCornerShape(16.dp))
                .padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = travel)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.paperRaised)
                    .border(2.dp, colors.ink, RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun PapierSearchField(
    value: String,
    modifier: Modifier = Modifier,
    hint: String = "Search",
    onValueChange: (String) -> Unit
) {
    val colors = PapierTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.paperSunken)
            .border(1.dp, colors.ink, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                text = "⌕",
                style = TextStyle(color = colors.inkSoft, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.accent),
                    textStyle = TextStyle(
                        color = colors.ink,
                        fontSize = 16.sp,
                        fontFamily = PapierTheme.type.bodyFamily
                    ),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            androidx.compose.foundation.text.BasicText(
                                text = hint,
                                style = TextStyle(color = colors.inkFaint, fontSize = 16.sp),
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                )
            }
        }
    }
}

@Composable
fun NoteTitleText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    val colors = PapierTheme.colors
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = colors.ink,
            fontSize = (PapierTheme.type.titleSize.value * PapierTheme.fontScale).sp,
            fontWeight = displayWeightForPack(PapierTheme.pack),
            fontFamily = PapierTheme.type.displayFamily
        )
    )
}

@Composable
fun BodySnippetText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 3
) {
    val colors = PapierTheme.colors
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = colors.inkSoft,
            fontSize = (16f * PapierTheme.fontScale).sp,
            fontFamily = PapierTheme.type.bodyFamily,
            lineHeight = (24f * PapierTheme.fontScale).sp
        )
    )
}

@Composable
fun CaptionText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PapierTheme.colors.inkFaint
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = color,
            fontSize = 12.sp,
            fontFamily = PapierTheme.type.monoFamily
        )
    )
}

/** Flat anime halftone dot paper background. */
@Composable
fun HalftonePaper(
    modifier: Modifier = Modifier,
    dotColor: Color = PapierTheme.colors.line,
    dotGap: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.background(PapierTheme.colors.paper)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val gapPx = dotGap.toPx()
            var y = gapPx / 2
            while (y < size.height) {
                var x = gapPx / 2
                while (x < size.width) {
                    drawCircle(color = dotColor.copy(alpha = 0.35f), radius = 1.2.dp.toPx(), center = Offset(x, y))
                    x += gapPx
                }
                y += gapPx
            }
        }
        content()
    }
}

/** Phone bottom InkBar — custom nav, no Material NavigationBar. */
@Composable
fun InkBar(
    modifier: Modifier = Modifier,
    selected: Int,
    onSelect: (Int) -> Unit,
    onCompose: () -> Unit
) {
    val colors = PapierTheme.colors
    val tabs = listOf("Notes", "Folders", "Search", "Settings")
    val glyphs = listOf("✎", "▤", "⌕", "⚙")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.paperRaised)
                .border(2.dp, colors.ink, RoundedCornerShape(14.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { i, label ->
                val isSel = i == selected
                val press = rememberPapierPress()
                Column(
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .then(press.modifier)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) colors.ink else Color.Transparent)
                        .semantics { this[SemanticsProperties.Selected] = isSel }
                        .clickable(
                            role = Role.Tab,
                            indication = null,
                            interactionSource = press.source,
                            onClick = { onSelect(i) }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = glyphs[i],
                        style = TextStyle(
                            color = if (isSel) colors.paper else colors.ink,
                            fontSize = 18.sp, fontWeight = FontWeight.Black
                        )
                    )
                    androidx.compose.foundation.text.BasicText(
                        text = label,
                        style = TextStyle(
                            color = if (isSel) colors.paper else colors.inkSoft,
                            fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                    if (isSel) {
                        Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent))
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
            val composePress = rememberPapierPress()
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .offset(y = (-2).dp)
            ) {
                Box(modifier = Modifier.matchParentSize().offset(x = 0.dp, y = 3.dp).clip(RoundedCornerShape(14.dp)).background(colors.shadow))
                Box(
                    modifier = Modifier.matchParentSize()
                        .then(composePress.modifier)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent)
                        .border(2.dp, colors.ink, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, indication = null, interactionSource = composePress.source, onClick = onCompose),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        text = "+",
                        style = TextStyle(color = colors.accentInk, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    )
                }
            }
        }
    }
}
