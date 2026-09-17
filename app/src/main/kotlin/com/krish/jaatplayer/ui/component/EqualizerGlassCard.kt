package com.krish.jaatplayer.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.krish.jaatplayer.R
import com.krish.jaatplayer.constants.JaatStyleMode
import com.krish.jaatplayer.eq.audio.Reverb3DPreset
import com.krish.jaatplayer.ui.screens.equalizer.axion.AxionEqViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EqualizerGlassCard(
    onOpenAdvancedEq: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AxionEqViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsState()
    val bandGains by viewModel.bandGains.collectAsState()
    val reverbPreset by viewModel.reverbPreset.collectAsState()
    val jaatStylesEnabled by viewModel.jaatStylesEnabled.collectAsState()
    val jaatStyleMode by viewModel.jaatStyleMode.collectAsState()

    val menuGlassConfig = LocalMenuGlassConfig.current
    val cardShape = RoundedCornerShape(28.dp)
    val isMenuEnabled = menuGlassConfig.isEnabledFor(GlassMenu.EQUALIZER)
    val useGlass = isMenuEnabled && isGlassSupported()
    val glassEffectConfig = menuGlassConfig.toGlassEffectConfig(globalEnabled = isMenuEnabled)

    // Jaat Signature Presets
    val jaatSignaturePreset = floatArrayOf(150f, 100f, 50f, 0f, -20f, 0f, 80f, 150f, 200f, 150f)
    val jaatBassBoostPreset = floatArrayOf(500f, 400f, 250f, 100f, 0f, -50f, 0f, 100f, 200f, 300f)
    val jaatPureClarityPreset = floatArrayOf(-100f, -50f, 0f, 50f, 150f, 250f, 300f, 250f, 150f, 100f)
    val jaatAcousticPreset = floatArrayOf(150f, 150f, 50f, 75f, 100f, 75f, 125f, 175f, 150f, 75f)

    // Dolby Atmos Presets
    val dolbyOpenPreset = floatArrayOf(150f, 180f, 220f, 180f, 160f, 210f, 250f, 280f, 180f, 80f)
    val dolbyRichPreset = floatArrayOf(100f, 160f, 200f, 220f, 280f, 260f, 240f, 200f, 150f, 50f)
    val dolbyFocusedPreset = floatArrayOf(-300f, -50f, 130f, 180f, 220f, 120f, 140f, 100f, -50f, -300f)

    // Dirac Audio Presets
    val diracMusicPreset = floatArrayOf(200f, 140f, 80f, 0f, 30f, 80f, 140f, 200f, 280f, 350f)
    val diracMoviePreset = floatArrayOf(300f, 250f, 150f, 0f, 70f, 120f, 180f, 250f, 320f, 400f)
    val diracGamePreset = floatArrayOf(150f, 250f, 200f, 0f, 80f, 150f, 300f, 450f, 400f, 280f)

    // Jaat Signature active states & label
    val isJaatSig = enabled && bandGains.contentEquals(jaatSignaturePreset)
    val isJaatBass = enabled && bandGains.contentEquals(jaatBassBoostPreset)
    val isJaatClarity = enabled && bandGains.contentEquals(jaatPureClarityPreset)
    val isJaatAcoustic = enabled && bandGains.contentEquals(jaatAcousticPreset)
    val isJaatActive = isJaatSig || isJaatBass || isJaatClarity || isJaatAcoustic
    val jaatLabel = when {
        isJaatSig -> "Jaat (Signature)"
        isJaatBass -> "Jaat (Bass)"
        isJaatClarity -> "Jaat (Clarity)"
        isJaatAcoustic -> "Jaat (Acoustic)"
        else -> "Jaat Signature"
    }

    // Dolby Atmos active states & label
    val isDolbyOpen = enabled && bandGains.contentEquals(dolbyOpenPreset)
    val isDolbyRich = enabled && bandGains.contentEquals(dolbyRichPreset)
    val isDolbyFocused = enabled && bandGains.contentEquals(dolbyFocusedPreset)
    val isDolbyActive = isDolbyOpen || isDolbyRich || isDolbyFocused
    val dolbyLabel = when {
        isDolbyOpen -> "Dolby (Open)"
        isDolbyRich -> "Dolby (Rich)"
        isDolbyFocused -> "Dolby (Focused)"
        else -> "Dolby Atmos"
    }

    // Dirac Audio active states & label
    val isDiracMusic = enabled && bandGains.contentEquals(diracMusicPreset)
    val isDiracMovie = enabled && bandGains.contentEquals(diracMoviePreset)
    val isDiracGame = enabled && bandGains.contentEquals(diracGamePreset)
    val isDiracActive = isDiracMusic || isDiracMovie || isDiracGame
    val diracLabel = when {
        isDiracMusic -> "Dirac (Music)"
        isDiracMovie -> "Dirac (Movie)"
        isDiracGame -> "Dirac (Game)"
        else -> "Dirac Audio"
    }

    // 3D Reverb active state & label
    val is3DReverbActive = enabled && reverbPreset != Reverb3DPreset.NONE
    val reverbLabel = when (reverbPreset) {
        Reverb3DPreset.NORMAL -> "3D (Normal)"
        Reverb3DPreset.CONCERT -> "3D (Concert)"
        Reverb3DPreset.TECHNO -> "3D (Techno)"
        else -> "3D Reverb"
    }

    // Jaat Styles active state & label
    val isJaatStyleActive = jaatStylesEnabled && enabled
    val jaatStyleLabel = if (isJaatStyleActive) {
        when (jaatStyleMode) {
            JaatStyleMode.BASS_DROP -> "Styles (Bass)"
            JaatStyleMode.MASHUP -> "Styles (Mashup)"
            JaatStyleMode.SPATIAL_8D -> "Styles (8D)"
            JaatStyleMode.FILTER_SWEEP -> "Styles (DJ)"
        }
    } else {
        "Jaat Styles FX"
    }

    Surface(
        modifier = modifier
            .widthIn(max = 320.dp)
            .aspectRatio(1f)
            .padding(8.dp)
            .let { mod ->
                if (useGlass) {
                    mod.clip(cardShape).liquidGlass(config = glassEffectConfig, shape = cardShape)
                } else {
                    mod
                }
            },
        shape = cardShape,
        color = if (useGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (useGlass) 0.dp else 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Title & Advanced EQ Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onOpenAdvancedEq,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.tune),
                        contentDescription = "Advanced Equalizer Options",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Square Grid Effects
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    // Jaat Signature button with mode cycling
                    ToggleButton(
                        checked = isJaatActive,
                        onCheckedChange = {
                            if (!enabled) viewModel.setEnabled(true)
                            when {
                                isJaatSig -> viewModel.setBandsGains(jaatBassBoostPreset, fromUser = true)
                                isJaatBass -> viewModel.setBandsGains(jaatPureClarityPreset, fromUser = true)
                                isJaatClarity -> viewModel.setBandsGains(jaatAcousticPreset, fromUser = true)
                                isJaatAcoustic -> viewModel.reset()
                                else -> viewModel.setBandsGains(jaatSignaturePreset, fromUser = true)
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = jaatLabel,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                            label = "jaatLabelAnim"
                        ) { label ->
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }

                    // Dolby Atmos button with mode cycling
                    ToggleButton(
                        checked = isDolbyActive,
                        onCheckedChange = {
                            if (!enabled) viewModel.setEnabled(true)
                            when {
                                isDolbyOpen -> viewModel.setBandsGains(dolbyRichPreset, fromUser = true)
                                isDolbyRich -> viewModel.setBandsGains(dolbyFocusedPreset, fromUser = true)
                                isDolbyFocused -> viewModel.reset()
                                else -> viewModel.setBandsGains(dolbyOpenPreset, fromUser = true)
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = dolbyLabel,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                            label = "dolbyLabelAnim"
                        ) { label ->
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    // Dirac Audio button with mode cycling
                    ToggleButton(
                        checked = isDiracActive,
                        onCheckedChange = {
                            if (!enabled) viewModel.setEnabled(true)
                            when {
                                isDiracMusic -> viewModel.setBandsGains(diracMoviePreset, fromUser = true)
                                isDiracMovie -> viewModel.setBandsGains(diracGamePreset, fromUser = true)
                                isDiracGame -> viewModel.reset()
                                else -> viewModel.setBandsGains(diracMusicPreset, fromUser = true)
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = diracLabel,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                            label = "diracLabelAnim"
                        ) { label ->
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }

                    // 3D Reverb button with mode cycling
                    ToggleButton(
                        checked = is3DReverbActive,
                        onCheckedChange = {
                            if (!enabled) viewModel.setEnabled(true)
                            val nextPreset = when (reverbPreset) {
                                Reverb3DPreset.NONE -> Reverb3DPreset.NORMAL
                                Reverb3DPreset.NORMAL -> Reverb3DPreset.CONCERT
                                Reverb3DPreset.CONCERT -> Reverb3DPreset.TECHNO
                                Reverb3DPreset.TECHNO -> Reverb3DPreset.NONE
                            }
                            viewModel.setReverbPreset(nextPreset)
                        },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = reverbLabel,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                            label = "reverbLabelAnim"
                        ) { label ->
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }

                // Jaat Styles FX button with mode cycling
                ToggleButton(
                    checked = isJaatStyleActive,
                    onCheckedChange = {
                        if (!enabled) viewModel.setEnabled(true)
                        if (!jaatStylesEnabled) {
                            viewModel.setJaatStylesEnabled(true)
                            viewModel.setJaatStyleMode(JaatStyleMode.BASS_DROP)
                        } else {
                            when (jaatStyleMode) {
                                JaatStyleMode.BASS_DROP -> viewModel.setJaatStyleMode(JaatStyleMode.MASHUP)
                                JaatStyleMode.MASHUP -> viewModel.setJaatStyleMode(JaatStyleMode.SPATIAL_8D)
                                JaatStyleMode.SPATIAL_8D -> viewModel.setJaatStyleMode(JaatStyleMode.FILTER_SWEEP)
                                JaatStyleMode.FILTER_SWEEP -> viewModel.setJaatStylesEnabled(false)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(jaatStyleLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
