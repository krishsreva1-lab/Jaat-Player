package com.krish.jaatplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.krish.jaatplayer.ui.component.backdrop.Backdrop
import com.krish.jaatplayer.ui.component.backdrop.backdrops.layerBackdrop
import com.krish.jaatplayer.ui.component.backdrop.backdrops.rememberLayerBackdrop

/** True while a settings screen is being drawn on the glass backdrop; its cards then render as glass. */
val LocalSettingsGlass = compositionLocalOf { false }

/**
 * Wraps the navigation content. When [active], draws a soft colour backdrop behind it and makes every
 * glass surface inside (the settings cards) sample that backdrop instead of the app's. When not active
 * it provides the normal app backdrop and draws nothing, so everything else is untouched.
 */
@Composable
fun BoxScope.SettingsGlassLayer(
    active: Boolean,
    appBackdrop: Backdrop,
    content: @Composable () -> Unit,
) {
    val settingsBackdrop = rememberLayerBackdrop()
    if (active) {
        SettingsGlassBackground(Modifier.matchParentSize().layerBackdrop(settingsBackdrop))
    }
    CompositionLocalProvider(
        LocalAppBackdrop provides if (active) settingsBackdrop else appBackdrop,
        LocalSettingsGlass provides active,
    ) {
        content()
    }
}

/**
 * Glass needs something to refract: soft theme-coloured light pools on the surface colour, static
 * (no animation) so it costs next to nothing.
 */
@Composable
private fun SettingsGlassBackground(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    val strength = if (isDark) 0.38f else 0.30f
    Box(
        modifier = modifier
            .background(scheme.surface)
            .drawBehind {
                fun pool(color: Color, center: Offset, radius: Float) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = strength), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                    )
                }
                pool(scheme.primary, Offset(size.width * 0.12f, size.height * 0.10f), size.width * 0.95f)
                pool(scheme.tertiary, Offset(size.width * 0.95f, size.height * 0.42f), size.width * 0.85f)
                pool(scheme.secondary, Offset(size.width * 0.20f, size.height * 0.92f), size.width * 0.90f)
            },
    )
}
