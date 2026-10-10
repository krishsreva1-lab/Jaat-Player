package com.krish.jaatplayer.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MintCardBackground = Color(0xFF0E2E23)
private val MintTitleText = Color(0xFFE6F7F2)
private val MintPrimaryAccent = Color(0xFF2DD4A0)
private val MintOnPrimaryText = Color(0xFF0A241B)

@Composable
fun SupportProjectCard(
    modifier: Modifier = Modifier,
    onDonateClick: () -> Unit = {},
    onLaterClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MintCardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Support the Project",
                style = MaterialTheme.typography.headlineSmall,
                color = MintTitleText,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            SupportProjectIllustration()

            Spacer(Modifier.height(20.dp))

            // Filled mint button: Donate
            Button(
                onClick = onDonateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintPrimaryAccent,
                    contentColor = MintOnPrimaryText
                )
            ) {
                Text(
                    text = "Donate",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // Outlined mint button: Later
            OutlinedButton(
                onClick = onLaterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, MintPrimaryAccent),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MintPrimaryAccent
                )
            ) {
                Text(
                    text = "Later",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SupportProjectCardPreview() {
    MaterialTheme {
        SupportProjectCard()
    }
}
