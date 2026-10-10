package com.krish.jaatplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.krish.jaatplayer.BuildConfig
import com.krish.jaatplayer.R
import com.krish.jaatplayer.ui.component.GlassComponent
import com.krish.jaatplayer.ui.component.LocalGlassEffectConfig
import com.krish.jaatplayer.ui.component.isGlassSupported
import com.krish.jaatplayer.ui.component.liquidGlass

/**
 * One-time "what's new" popup shown on the Home screen the first time the app is
 * opened after installing a new version (see the `lastOpenedVersionCode` check in
 * MainActivity). Renders as true Liquid Glass — sampling the real Home screen
 * behind it — when Liquid Glass beta + the Menu surface toggle are on; otherwise
 * falls back to the original solid Material3 card.
 */
@Composable
fun WelcomeDialog(
    onDismissRequest: () -> Unit
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlassEffect = remember(glassConfig) {
        glassConfig.isEnabledFor(GlassComponent.MENU) && isGlassSupported()
    }

    if (useGlassEffect) {
        WelcomeDialogGlass(onDismissRequest, glassConfig)
    } else {
        WelcomeDialogMaterial(onDismissRequest)
    }
}

@Composable
private fun WelcomeDialogMaterial(
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WelcomeDialogContent(onDismissRequest)
            }
        }
    }
}

/** Same-window [Popup] + [liquidGlass] so the backdrop blur samples the real Home
 * screen (mini player, nav bar and all) instead of a Dialog's separate window. */
@Composable
private fun WelcomeDialogGlass(
    onDismissRequest: () -> Unit,
    glassConfig: com.krish.jaatplayer.ui.component.GlassEffectConfig
) {
    var isPopupActive by remember { mutableStateOf(true) }
    var isVisible by remember { mutableStateOf(true) }
    val requestDismiss: () -> Unit = { isVisible = false }

    if (isPopupActive) {
        Popup(
            onDismissRequest = requestDismiss,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                excludeFromSystemGesture = false,
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = requestDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 14 },
                        animationSpec = tween(220)
                    ) + scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(200)),
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            isPopupActive = false
                            onDismissRequest()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .liquidGlass(config = glassConfig, shape = RoundedCornerShape(28.dp))
                            .clickable(enabled = false) {},
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 20.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            WelcomeDialogContent(requestDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.WelcomeDialogContent(
    onDismissRequest: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    run {
        // Main Header
                WelcomeAppCard()

                WelcomeSectionCard(title = "Developer") {
                    WelcomeActionRow(
                        icon = painterResource(R.drawable.ic_instagram_new),
                        title = "Instagram",
                        subtitle = "@krishsreva444",
                        onClick = { uriHandler.openUri("https://instagram.com/krishsreva444") }
                    )
                    WelcomeDivider()
                    WelcomeActionRow(
                        icon = painterResource(R.drawable.website),
                        title = "Website",
                        subtitle = "jaatplayerr.web.app",
                        onClick = { uriHandler.openUri("https://jaatplayerr.web.app/") }
                    )
                    WelcomeDivider()
                    WelcomeActionRow(
                        icon = painterResource(R.drawable.person),
                        title = "About Developer",
                        subtitle = "Know more about me",
                        onClick = { uriHandler.openUri("https://jaatplayerr.web.app/") }
                    )
                }

                WelcomeSectionCard(title = "Community") {
                    WelcomeActionRow(
                        icon = painterResource(R.drawable.ic_telegram_new),
                        title = "Telegram Channel",
                        subtitle = "t.me/jaatplayerr",
                        onClick = { uriHandler.openUri("https://t.me/jaatplayerr") }
                    )
                }

                WelcomeSectionCard(title = "Support & Donate") {
                    WelcomeActionRow(
                        icon = painterResource(R.drawable.currency_rupee_upi),
                        title = "Donate via UPI",
                        subtitle = "GPay / PhonePe / Paytm / BHIM",
                        onClick = {
                            val upiUri = Uri.parse("upi://pay?pa=9887624399@fam&pn=Jaat%20Player&tn=Support%20Jaat%20Player&cu=INR")
                            val intent = Intent(Intent.ACTION_VIEW, upiUri)
                            try {
                                context.startActivity(Intent.createChooser(intent, "Pay with UPI"))
                            } catch (_: Exception) {
                                Toast.makeText(context, "No UPI app found on device", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                WelcomeSectionCard(title = "App") {
                    WelcomeActionRow(
                        icon = painterResource(R.drawable.github),
                        title = "GitHub",
                        subtitle = "krishsreva1-lab/Jaat-Player",
                        onClick = { uriHandler.openUri("https://github.com/krishsreva1-lab/Jaat-Player") }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { uriHandler.openUri("https://github.com/krishsreva1-lab/Jaat-Player") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.star),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Star the Repo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
    }
}

@Composable
private fun WelcomeAppCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            AsyncImage(
                model = com.krish.jaatplayer.utils.AppLogo.fullIconRes(),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Jaat Player",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun WelcomeActionRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "rowScale",
    )
    val tint = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = tint.copy(alpha = 0.10f),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tint,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun WelcomeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 78.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}
