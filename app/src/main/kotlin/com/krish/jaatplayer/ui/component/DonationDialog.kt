package com.krish.jaatplayer.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
        SupportProjectCard(
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
