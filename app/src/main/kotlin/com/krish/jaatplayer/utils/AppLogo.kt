

package com.krish.jaatplayer.utils

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import com.krish.jaatplayer.R
import com.krish.jaatplayer.constants.EnableLegacyIconKey
import kotlinx.coroutines.runBlocking

/**
 * Single source of truth for "which logo is the app currently using" - New Logo or Legacy Logo.
 * Every place the app logo/watermark appears (splash, player, nav drawer, notifications, widgets,
 * lock screen, media session artwork) should read from here so the choice is always applied
 * consistently everywhere, never mixed.
 */
object AppLogo {

    /** Transparent, no-background glyph - for watermarks, placeholders, in-app logo art. */
    @Composable
    fun glyphRes(): Int {
        val (isLegacy) = rememberPreference(EnableLegacyIconKey, defaultValue = false)
        return if (isLegacy) R.mipmap.legacy_icon_foreground else R.drawable.ic_launcher_nobg
    }

    /** Same as [glyphRes] but for non-Compose call sites (notification builders, bitmaps). */
    @DrawableRes
    fun glyphResBlocking(context: Context): Int {
        val isLegacy = runBlocking { context.dataStore.get(EnableLegacyIconKey, false) }
        return if (isLegacy) R.mipmap.legacy_icon_foreground else R.drawable.ic_launcher_nobg
    }

    /** Full adaptive-icon mipmap (background + foreground baked together) - for the splash/welcome screen and image watermarks. */
    @Composable
    fun fullIconRes(): Int {
        val (isLegacy) = rememberPreference(EnableLegacyIconKey, defaultValue = false)
        return if (isLegacy) R.mipmap.legacy_icon else R.mipmap.ic_launcher
    }

    @DrawableRes
    fun fullIconResBlocking(context: Context): Int {
        val isLegacy = runBlocking { context.dataStore.get(EnableLegacyIconKey, false) }
        return if (isLegacy) R.mipmap.legacy_icon else R.mipmap.ic_launcher
    }
}
