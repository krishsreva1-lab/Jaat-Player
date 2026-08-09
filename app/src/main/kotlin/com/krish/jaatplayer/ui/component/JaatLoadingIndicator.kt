package com.krish.jaatplayer.ui.component

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun JaatLoadingIndicator(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = true,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    if (isRefreshing) {
        val infiniteTransition = rememberInfiniteTransition(label = "flicker")
        
        val letters = listOf("J", "a", "a", "t")
        val alphas = letters.mapIndexed { index, _ ->
            infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        val start = index * 150
                        0.2f at 0
                        0.2f at start
                        1f at (start + 300).coerceAtMost(1000)
                        0.2f at (start + 600).coerceAtMost(1000)
                        0.2f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha_$index"
            )
        }

        Box(
            modifier = modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                letters.forEachIndexed { index, letter ->
                    Text(
                        text = letter,
                        style = style.copy(
                            fontWeight = FontWeight.Medium,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                        modifier = Modifier.alpha(alphas[index].value)
                    )
                }
            }
        }
    }
}
