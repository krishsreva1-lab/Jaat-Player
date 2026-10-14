package com.krish.jaatplayer.ui.component

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private val GlassShape = RoundedCornerShape(32.dp)
private val ButtonShape = RoundedCornerShape(26.dp)

/**
 * "Support the project" popup as a frosted-glass card.
 *
 * The popup lives in its own window, so the app's glass backdrop can't be sampled here. Instead the
 * window itself asks the system to blur what is behind it (Android 12+; older versions just get the
 * dim), and the card is a translucent glass surface with a bright rim and highlight on top of that.
 */
@Composable
fun DonationDialog(
    onDismiss: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setDimAmount(0.32f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = 40 }
            }
        }
        GlassSupportProjectCard(
            modifier = modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 360.dp),
            onDonateClick = {
                onDonate()
                onDismiss()
            },
            onLaterClick = onDismiss
        )
    }
}

@Composable
private fun GlassSupportProjectCard(
    modifier: Modifier = Modifier,
    onDonateClick: () -> Unit,
    onLaterClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, GlassShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(GlassShape)
            // Tinted glass body: lighter at the top where light would hit, deeper at the bottom.
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.26f),
                        Color(0xFF9FB4C7).copy(alpha = 0.14f),
                        Color.Black.copy(alpha = 0.22f),
                    )
                )
            )
            // Bright rim that fades out along the sides, like a refracting glass edge.
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.30f))
                ),
                shape = GlassShape
            )
    ) {
        // Soft specular highlight across the top of the card.
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
                )
        )
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Support the Project",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            SupportProjectIllustration()
            Spacer(Modifier.height(20.dp))
            // Pure white button, black text.
            Button(
                onClick = onDonateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
            ) {
                Text(text = "Donate", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            // Glass button for the secondary action.
            OutlinedButton(
                onClick = onLaterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = ButtonShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White
                )
            ) {
                Text(text = "Later", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}
