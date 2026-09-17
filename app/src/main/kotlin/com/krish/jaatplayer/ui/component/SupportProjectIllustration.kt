package com.krish.jaatplayer.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.krish.jaatplayer.R

@Composable
fun SupportProjectIllustration(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.dev_hoodie_art),
            contentDescription = "Support Project Developer Illustration",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(180.dp)
        )
    }
}
