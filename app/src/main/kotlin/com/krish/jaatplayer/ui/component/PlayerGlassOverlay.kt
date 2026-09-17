package com.krish.jaatplayer.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Same-window replacement for `Dialog()` / `AlertDialog()` / `ModalBottomSheet()` for
 * any surface that needs to render [Modifier.liquidGlass].
 *
 * Those three APIs each open a *separate* Android window. This app's backdrop blur
 * ([com.krish.jaatplayer.ui.component.backdrop.backdrops.LayerBackdrop]) positions the
 * sampled layer using per-window [androidx.compose.ui.layout.LayoutCoordinates], so it
 * can only ever line up correctly when the glass surface lives in the SAME window as
 * the content it is sampling. Opening it in a separate window instead produces a
 * misaligned/frozen crop of whatever was captured before the window opened.
 *
 * [PlayerGlassOverlay] never leaves the current window — it is a plain sibling
 * composable, exactly like the already-working sleep timer / equalizer overlays in
 * `Player.kt`. Because it never creates a new window, it also inherits whatever
 * [LocalAppBackdrop] is currently provided at its call site (e.g. the Player screen's
 * own `playerBackdrop`), so the glass genuinely samples the live content behind it
 * instead of a stale capture.
 *
 * [visible] drives the enter/exit animation. The overlay keeps composing [content] for
 * the duration of the exit animation (so it can animate out) via an internal "active"
 * flag, then leaves the composition entirely once the exit finishes — it costs nothing
 * while hidden, same as the pattern it replaces.
 *
 * [alignment] controls where [content] sits within the overlay: [Alignment.Center] for
 * a dialog-style card, [Alignment.BottomCenter] for a bottom-sheet-style panel.
 */
@Composable
fun PlayerGlassOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    var isActive by remember { mutableStateOf(visible) }
    if (visible) isActive = true

    if (!isActive) return

    BackHandler(enabled = visible, onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
        contentAlignment = alignment,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.9f, animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
            exit = scaleOut(targetScale = 0.9f, animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
        ) {
            DisposableEffect(Unit) {
                onDispose { isActive = false }
            }

            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // absorb clicks so tapping the card itself doesn't dismiss
                )
            ) {
                content()
            }
        }
    }
}

/**
 * Dialog-shaped content (title + body + buttons, card centered on screen) rendered
 * through [PlayerGlassOverlay] instead of `Dialog()`/`AlertDialog()`. Layout mirrors
 * [DefaultDialog] in Dialog.kt exactly — same padding, same shape, same use of
 * [GlassEffectConfig] — the only difference is the window: this one stays in the
 * current window so its glass samples the correct, live [LocalAppBackdrop].
 *
 * Use this to replace a `Dialog()`/`AlertDialog()` call that applies
 * [Modifier.liquidGlass] — swap the wrapper, keep the same title/content/buttons.
 */
@Composable
fun PlayerGlassDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    glassEffectConfig: GlassEffectConfig?,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val useGlass = glassEffectConfig != null && glassEffectConfig.globalEnabled && isGlassSupported()
    val dialogShape = RoundedCornerShape(28.dp)

    PlayerGlassOverlay(
        visible = visible,
        onDismissRequest = onDismissRequest,
        alignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier
                .padding(24.dp)
                .widthIn(max = 320.dp)
                .let { mod ->
                    if (useGlass) {
                        mod.clip(dialogShape)
                            .liquidGlass(config = glassEffectConfig!!, shape = dialogShape)
                    } else {
                        mod
                    }
                },
            shape = dialogShape,
            color = if (useGlass) Color.Transparent else AlertDialogDefaults.containerColor,
            tonalElevation = if (useGlass) 0.dp else AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = Modifier.padding(24.dp),
            ) {
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(Modifier.align(Alignment.Start)) {
                                title()
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                content()

                if (buttons != null) {
                    Spacer(Modifier.height(24.dp))
                    FlowRow(modifier = Modifier.align(Alignment.End)) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                                buttons()
                            }
                        }
                    }
                }
            }
        }
    }
}
