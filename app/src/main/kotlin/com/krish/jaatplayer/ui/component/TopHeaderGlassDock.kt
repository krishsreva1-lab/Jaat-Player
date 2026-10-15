package com.krish.jaatplayer.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.krish.jaatplayer.R
import com.krish.jaatplayer.ui.component.backdrop.backdrops.layerBackdrop
import com.krish.jaatplayer.ui.component.backdrop.backdrops.rememberCombinedBackdrop
import com.krish.jaatplayer.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.krish.jaatplayer.ui.component.backdrop.catalog.utils.DampedDragAnimation
import com.krish.jaatplayer.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.krish.jaatplayer.ui.component.backdrop.drawBackdrop
import com.krish.jaatplayer.ui.component.backdrop.effects.blur
import com.krish.jaatplayer.ui.component.backdrop.effects.lens
import com.krish.jaatplayer.ui.component.backdrop.effects.vibrancy
import com.krish.jaatplayer.ui.component.backdrop.highlight.Highlight
import com.krish.jaatplayer.ui.component.backdrop.shadow.InnerShadow
import com.krish.jaatplayer.ui.component.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private data class TopHeaderActionItem(
    val id: String,
    val iconRes: Int?,
    val imageUrl: String?,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Glass dock with a water-drop puck for top bar options
 * (History, Stats, Listen Together, Settings).
 */
@Composable
fun TopHeaderGlassDock(
    showHistoryButton: Boolean,
    listenTogetherInTopBar: Boolean,
    accountImageUrl: String?,
    pureBlack: Boolean,
    onHistoryClick: () -> Unit,
    onStatsClick: () -> Unit,
    onListenTogetherClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyText = stringResource(R.string.history)
    val statsText = stringResource(R.string.stats)
    val togetherText = stringResource(R.string.together)
    val accountText = stringResource(R.string.account)

    val items = remember(showHistoryButton, listenTogetherInTopBar, accountImageUrl) {
        buildList {
            if (showHistoryButton) {
                add(TopHeaderActionItem("history", R.drawable.music_history, null, historyText, onHistoryClick))
            }
            add(TopHeaderActionItem("stats", R.drawable.stats, null, statsText, onStatsClick))
            if (listenTogetherInTopBar) {
                add(TopHeaderActionItem("listen_together", R.drawable.group_outlined, null, togetherText, onListenTogetherClick))
            }
            add(TopHeaderActionItem("settings", R.drawable.settings, accountImageUrl, accountText, onSettingsClick))
        }
    }
    val count = items.size
    if (count == 0) return

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()
    val backdrop = LocalAppBackdrop.current

    val dockShape = RoundedCornerShape(percent = 50)
    val puckShape = RoundedCornerShape(percent = 50)
    val barPadding = 3.dp
    val itemWidth = 36.dp
    val dockHeight = 38.dp

    val contentColor = if (useGlass) glassConfig.textColor else MaterialTheme.colorScheme.onSurface
    val accentColor = if (useGlass) glassConfig.textColor else MaterialTheme.colorScheme.primary

    val puckWash = if (glassConfig.puckColor.isSpecified) {
        glassConfig.puckColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFF2F2F2)
    } else {
        Color(28, 27, 28)
    }
    val puckRestAlpha = glassConfig.puckOpacity.coerceIn(0f, 1f).coerceAtMost(0.35f)

    val tabWidthPx = with(density) { itemWidth.toPx() }
    val paddingPx = with(density) { barPadding.toPx() }
    val totalWidth = itemWidth * count + barPadding * 2
    val totalWidthPx = with(density) { totalWidth.toPx() }

    val offsetAnimation = remember(count) { Animatable(0f) }
    val panelOffset by remember(density, totalWidthPx) {
        derivedStateOf {
            if (totalWidthPx <= 0f) return@derivedStateOf 0f
            val rawFraction = offsetAnimation.value / totalWidthPx
            if (rawFraction.isNaN()) return@derivedStateOf 0f
            val fraction = rawFraction.fastCoerceIn(-1f, 1f)
            with(density) { 3f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
        }
    }

    var selectedIndex by remember(count) { mutableIntStateOf((count - 1).coerceAtLeast(0)) }
    val safeSelectedIndex = selectedIndex.coerceIn(0, (count - 1).coerceAtLeast(0))

    val dampedDragAnimation = remember(animationScope, count, tabWidthPx) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = safeSelectedIndex.toFloat(),
            valueRange = 0f..(count - 1).coerceAtLeast(0).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.25f,
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
                selectedIndex = targetIndex
                updateValue(targetIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                items.getOrNull(targetIndex)?.onClick?.invoke()
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            },
            velocityDampingRatio = 1f,
        )
    }

    val interactiveHighlight = remember(animationScope, tabWidthPx) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) paddingPx + (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - paddingPx - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f
                )
            }
        )
    }

    val tabsBackdrop = rememberLayerBackdrop()

    @Composable
    fun ItemIconContent(index: Int, modifier: Modifier, scale: Float = 1f) {
        val item = items.getOrNull(index) ?: return
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.contentDescription,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                )
            } else if (item.iconRes != null) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = item.contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
        }
    }

    Box(
        modifier = modifier
            .padding(end = 8.dp)
            .width(totalWidth)
            .height(dockHeight + barPadding * 2)
    ) {
        // Outer glass shell
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = panelOffset }
                .clip(dockShape)
                .then(
                    if (useGlass) {
                        Modifier.liquidGlass(config = glassConfig, shape = dockShape)
                    } else {
                        Modifier.background(
                            if (pureBlack) Color.Black.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                            dockShape
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (useGlass) glassConfig.textColor.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = dockShape
                )
                .then(interactiveHighlight.modifier)
                .padding(barPadding)
        ) {
            for (index in 0 until count) {
                val item = items[index]
                ItemIconContent(
                    index = index,
                    scale = if (index == safeSelectedIndex) lerp(1f, 1.1f, dampedDragAnimation.pressProgress) else 1f,
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .alpha(if (index == safeSelectedIndex) 1f else 0.7f)
                        .clip(puckShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selectedIndex = index
                            dampedDragAnimation.animateToValue(index.toFloat())
                            item.onClick()
                        }
                )
            }
        }

        // Hidden backdrop layer sampled by the puck
        if (useGlass) {
            val tabsBackdropScale = glassResolutionScale(1f)
            Row(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .padding(barPadding)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { dockShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(glassConfig.blurRadius.dp.toPx() * tabsBackdropScale)
                            lens(
                                12f.dp.toPx() * progress * tabsBackdropScale,
                                14f.dp.toPx() * progress * tabsBackdropScale
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                        },
                        onDrawSurface = { drawRect(Color.Transparent) },
                        backdropScale = tabsBackdropScale
                    )
                    .then(interactiveHighlight.modifier)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor))
            ) {
                for (index in 0 until count) {
                    ItemIconContent(
                        index = index,
                        scale = if (index == safeSelectedIndex) lerp(1f, 1.1f, dampedDragAnimation.pressProgress) else 1f,
                        modifier = Modifier.width(itemWidth).fillMaxHeight()
                    )
                }
            }
        }

        // Water-drop glass puck
        Box(
            Modifier
                .padding(barPadding)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidthPx + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidthPx + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .width(itemWidth)
                .fillMaxHeight()
                .then(
                    if (useGlass) {
                        Modifier.drawBackdrop(
                            backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                            shape = { puckShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                blur(2.5f.dp.toPx() * (1f - progress))
                                lens(
                                    lerp(0f.dp.toPx(), 8f.dp.toPx(), progress),
                                    lerp(0f.dp.toPx(), 10f.dp.toPx(), progress),
                                    chromaticAberration = progress > 0.01f
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = lerp(0.5f, 1f, progress))
                            },
                            shadow = {
                                val progress = dampedDragAnimation.pressProgress
                                Shadow(alpha = lerp(0.35f, 1f, progress))
                            },
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(radius = 6f.dp * progress, alpha = progress)
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(puckWash.copy(alpha = puckRestAlpha * (1f - 0.75f * progress)))
                            }
                        )
                    } else {
                        Modifier
                            .graphicsLayer {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                            }
                            .clip(puckShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.85f + 0.15f * dampedDragAnimation.pressProgress
                                ),
                                puckShape
                            )
                    }
                )
        )

        // Selected icon drawn on top of the puck
        val puckIndex by remember(count) {
            derivedStateOf { dampedDragAnimation.value.fastRoundToInt().coerceIn(0, (count - 1).coerceAtLeast(0)) }
        }
        Box(
            Modifier
                .padding(barPadding)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidthPx + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidthPx + panelOffset
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    alpha = (1f - dampedDragAnimation.pressProgress * 3f).fastCoerceIn(0f, 1f)
                }
                .width(itemWidth)
                .fillMaxHeight()
                .clearAndSetSemantics {}
        ) {
            ItemIconContent(index = puckIndex, modifier = Modifier.fillMaxSize())
        }
    }
}
