package com.foxus.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foxus.app.R
import com.foxus.app.ui.components.FoxMascot
import com.foxus.app.ui.components.FoxMascotState
import com.foxus.app.ui.theme.FoxusTokens

@Composable
fun OnboardingScreen(
    onFinishOnboarding: (Int) -> Unit
) {
    var selectedLevel by remember { mutableIntStateOf(2) }
    val previewState = when (selectedLevel) {
        1 -> FoxMascotState.IDLE
        2 -> FoxMascotState.CURIOUS
        else -> FoxMascotState.CHALLENGE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.foxus_logo_official),
                    contentDescription = "Logotipo oficial FOXUS",
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                FoxMascot(state = previewState, size = 128.dp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Rompe la resistencia a empezar",
                style = MaterialTheme.typography.headlineMedium,
                color = FoxusTokens.WarmCream,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Google Gemma 4 divide tus tareas grandes en microacciones de 3 minutos.",
                style = MaterialTheme.typography.bodyLarge,
                color = FoxusTokens.TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(FoxusTokens.SurfaceCard)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Elige el estilo de acompañamiento de FOX",
                style = MaterialTheme.typography.titleMedium,
                color = FoxusTokens.FoxGold
            )

            val options = listOf(
                1 to "Suave · Invitaciones discretas",
                2 to "Equilibrado · Retos de 3 min (Recomendado)",
                3 to "Enérgico · Impulso activo + Smartwatch"
            )

            options.forEach { (lvl, label) ->
                val active = selectedLevel == lvl
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) FoxusTokens.DeepNavy else FoxusTokens.SurfaceElevated)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) FoxusTokens.FoxOrange else FoxusTokens.BorderSubtle,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedLevel = lvl }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) FoxusTokens.FoxOrange else FoxusTokens.SlateBlue)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = FoxusTokens.WarmCream,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onFinishOnboarding(selectedLevel) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FoxusTokens.FoxOrange,
                contentColor = FoxusTokens.DarkSurface
            )
        ) {
            Text(
                text = "ENTRAR A FOXUS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
