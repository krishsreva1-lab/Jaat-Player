package com.krish.jaatplayer.ui.screens.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.krish.jaatplayer.ui.component.GlassComponent
import com.krish.jaatplayer.ui.component.LocalAppBackdrop
import com.krish.jaatplayer.ui.component.LocalGlassEffectConfig
import com.krish.jaatplayer.ui.component.glassResolutionScale
import com.krish.jaatplayer.ui.component.isGlassSupported
import com.krish.jaatplayer.ui.component.liquidGlass
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

/**
 * Water-drop "puck" version of the Search page dock (Explore / Jaat Chart / Album) —
 * the same draggable, lens-refracting glass puck the home navigation bar uses
 * (see FloatingTabBar.ExpandedTabs), sized for three equal tabs.
 *
 * Needs [LocalAppBackdrop] to be a backdrop that does NOT contain this dock (SearchScreen
 * provides its own for that reason). With glass turned off / unsupported it falls back to
 * a flat, draggable puck so the setting never leaves the dock broken.
 */
@Composable
fun SearchPuckDock(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<Pair<Int, String>>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val tabsCount = tabs.size
    if (tabsCount == 0) return

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassSupported()
    val backdrop = LocalAppBackdrop.current

    val dockShape = RoundedCornerShape(percent = 50)
    val puckShape = RoundedCornerShape(percent = 50)
    val barPadding = 4.dp
    val tabHeight = 52.dp

    val contentColor = if (useGlass) glassConfig.textColor else MaterialTheme.colorScheme.onSurface
    val accentColor = if (useGlass) glassConfig.textColor else MaterialTheme.colorScheme.primary

    val puckWash = if (glassConfig.puckColor.isSpecified) {
        glassConfig.puckColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFF2F2F2)
    } else {
        Color(28, 27, 28)
    }
    // A resting wash of 0.8 hides almost everything behind the puck; on this dock keep it see-through
    // so the page really shows through the glass (a lower user setting still wins).
    val puckRestAlpha = glassConfig.puckOpacity.coerceIn(0f, 1f).coerceAtMost(0.3f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val tabWidth: Dp = minOf(112.dp, (maxWidth - barPadding * 2) / tabsCount)
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val paddingPx = with(density) { barPadding.toPx() }
        val totalWidth = tabWidth * tabsCount + barPadding * 2
        val totalWidthPx = tabWidthPx * tabsCount + paddingPx * 2

        // The whole bar nudges a few dp in the drag direction, like the nav bar.
        val offsetAnimation = remember(tabsCount) { Animatable(0f) }
        val panelOffset by remember(density, totalWidthPx) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val currentOnTabSelected = rememberUpdatedState(onTabSelected)
        var currentIndex by remember(tabsCount) {
            mutableIntStateOf(selectedTabIndex.coerceIn(0, tabsCount - 1))
        }
        var hasDraggedPuck = false

        val dampedDragAnimation = remember(animationScope, tabsCount, tabWidthPx) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { hasDraggedPuck = false },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    updateValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                    if (hasDraggedPuck) currentOnTabSelected.value(targetIndex)
                },
                onDrag = { _, dragAmount ->
                    if (dragAmount != Offset.Zero) hasDraggedPuck = true
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
                velocityDampingRatio = 1f,
            )
        }

        // Keep the puck on the selected tab when selection changes from a tap or from the
        // gyro tilt rather than from dragging the puck itself.
        var hasSynced by remember(tabsCount) { mutableStateOf(false) }
        LaunchedEffect(selectedTabIndex, tabsCount) {
            val index = selectedTabIndex.coerceIn(0, tabsCount - 1)
            currentIndex = index
            if (!hasSynced) {
                hasSynced = true
                dampedDragAnimation.updateValue(index.toFloat())
                return@LaunchedEffect
            }
            dampedDragAnimation.animateToValue(index.toFloat())
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
        fun TabContent(index: Int, modifier: Modifier, scale: Float = 1f) {
            val (iconRes, label) = tabs[index]
            Column(
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(Modifier.width(totalWidth).height(tabHeight + barPadding * 2)) {
            // ---- the bar (glass or solid) with the real, clickable tabs ----
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
                for (index in 0 until tabsCount) {
                    TabContent(
                        index = index,
                        scale = if (index == currentIndex) lerp(1f, 1.12f, dampedDragAnimation.pressProgress) else 1f,
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .alpha(if (index == selectedTabIndex) 1f else 0.6f)
                            .clip(puckShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(index) }
                    )
                }
            }

            // ---- hidden, accent-tinted copy of the tabs that the puck's glass samples ----
            if (useGlass) {
                val tabsBackdropScale = glassResolutionScale(1f)
                Row(
                    Modifier
                        .fillMaxWidth()
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
                                    15f.dp.toPx() * progress * tabsBackdropScale,
                                    18f.dp.toPx() * progress * tabsBackdropScale
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
                    for (index in 0 until tabsCount) {
                        TabContent(
                            index = index,
                            scale = if (index == currentIndex) lerp(1f, 1.12f, dampedDragAnimation.pressProgress) else 1f,
                            modifier = Modifier.width(tabWidth).fillMaxHeight()
                        )
                    }
                }
            }

            // ---- the draggable water-drop puck ----
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
                    .width(tabWidth)
                    .fillMaxHeight()
                    .then(
                        if (useGlass) {
                            Modifier.drawBackdrop(
                                backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                                shape = { puckShape },
                                effects = {
                                    val progress = dampedDragAnimation.pressProgress
                                    blur(3f.dp.toPx() * (1f - progress))
                                    lens(
                                        lerp(0f.dp.toPx(), 10f.dp.toPx(), progress),
                                        lerp(0f.dp.toPx(), 12f.dp.toPx(), progress),
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
                                    InnerShadow(radius = 8f.dp * progress, alpha = progress)
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

            // The selected icon + label drawn crisply on top of the puck (settled state only).
            val puckIndex by remember(tabsCount) {
                derivedStateOf { dampedDragAnimation.value.fastRoundToInt().coerceIn(0, tabsCount - 1) }
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
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clearAndSetSemantics {}
            ) {
                // Flat fallback puck is solid primary, so the selected tab uses onPrimary.
                if (useGlass) {
                    TabContent(index = puckIndex, modifier = Modifier.fillMaxSize())
                } else {
                    val (iconRes, label) = tabs[puckIndex]
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
