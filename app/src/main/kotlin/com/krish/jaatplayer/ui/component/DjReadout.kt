/**
 * DJ readout (BPM / key / transition type), ported from BeatWave (GPL-3.0) for Jaat Player.
 */

package com.krish.jaatplayer.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krish.jaatplayer.LocalPlayerConnection
import com.krish.jaatplayer.playback.audio.DjMixTier
import com.krish.jaatplayer.playback.dj.DjState
import com.krish.jaatplayer.playback.dj.TransitionStyle
import kotlin.math.roundToInt

@Composable
fun DjReadout(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val state by playerConnection.djState.collectAsState()
    if (!state.enabled) return

    // Analysis needs the track to play for a while before it reports anything.
    val text = state.describe() ?: "Analyzing\u2026"
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun DjState.describe(): String? {
    val current = buildList {
        if (currentBpm > 0f) add("${currentBpm.roundToInt()} BPM")
        currentKey?.let(::add)
        tierLabel()?.let(::add)
    }
    if (current.isEmpty()) return null
    val next = buildList {
        if (nextBpm > 0f) add("${nextBpm.roundToInt()} BPM")
        nextKey?.let(::add)
    }
    val head = current.joinToString(" \u00B7 ")
    return if (next.isEmpty()) head else "$head \u2192 ${next.joinToString(" \u00B7 ")}"
}

private fun DjState.tierLabel(): String? = when (tier) {
    DjMixTier.FULL_DJ -> when (style) {
        TransitionStyle.ECHO_FREEZE -> "echo"
        TransitionStyle.TAPE_STOP -> "brake"
        else -> "beatmatched"
    }
    DjMixTier.SMART_CROSSFADE -> when (style) {
        TransitionStyle.ECHO_FREEZE -> "echo"
        TransitionStyle.TAPE_STOP -> "brake"
        else -> "tempo matched"
    }
    DjMixTier.PLAIN_CROSSFADE -> when (style) {
        TransitionStyle.TAPE_STOP -> "brake"
        TransitionStyle.ECHO_FREEZE -> "echo"
        else -> "crossfade"
    }
    null -> null
}
