package com.foxus.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.FoxusUiState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: FoxusUiState,
    onSaveConnection: (baseUrl: String, demoToken: String, deviceToken: String) -> Unit,
    onUpdatePreferences: (level: Int, audio: Boolean, music: Boolean) -> Unit
) {
    var baseUrl by remember(uiState.settings.backendBaseUrl) {
        mutableStateOf(uiState.settings.backendBaseUrl)
    }
    var demoToken by remember(uiState.settings.demoAccessToken) {
        mutableStateOf(uiState.settings.demoAccessToken)
    }
    var deviceToken by remember(uiState.settings.deviceSharedToken) {
        mutableStateOf(uiState.settings.deviceSharedToken)
    }
    var sliderLevel by remember(uiState.settings.interventionLevel) {
        mutableFloatStateOf(uiState.settings.interventionLevel.toFloat())
    }
    var audioEnabled by remember(uiState.settings.audioEnabled) {
        mutableStateOf(uiState.settings.audioEnabled)
    }
    var musicEnabled by remember(uiState.settings.musicEnabled) {
        mutableStateOf(uiState.settings.musicEnabled)
    }
    var showServerConfig by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineMedium,
            color = FoxusTokens.WarmCream
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FoxusTokens.SurfaceCard)
                .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Energía de Acompañamiento (${sliderLevel.roundToInt()} / 3)",
                style = MaterialTheme.typography.titleMedium,
                color = FoxusTokens.FoxGold
            )
            Text(
                text = if (sliderLevel.roundToInt() == 0) {
                    "Modo descanso: FOX duerme y silencia alertas."
                } else {
                    "Ajusta qué tan activo es tu árbitro y compañero FOX."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = FoxusTokens.TextSecondary
            )
            Slider(
                value = sliderLevel,
                onValueChange = { sliderLevel = it },
                onValueChangeFinished = {
                    onUpdatePreferences(sliderLevel.roundToInt(), audioEnabled, musicEnabled)
                },
                valueRange = 0f..3f,
                steps = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sonidos y voz del Árbitro FOX", color = FoxusTokens.WarmCream)
                Switch(
                    checked = audioEnabled,
                    onCheckedChange = {
                        audioEnabled = it
                        onUpdatePreferences(sliderLevel.roundToInt(), it, musicEnabled)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Música durante retos activos", color = FoxusTokens.WarmCream)
                Switch(
                    checked = musicEnabled,
                    onCheckedChange = {
                        musicEnabled = it
                        onUpdatePreferences(sliderLevel.roundToInt(), audioEnabled, it)
                    }
                )
            }
        }

        TextButton(
            onClick = { showServerConfig = !showServerConfig },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = if (showServerConfig) "Ocultar conexión avanzada" else "Conexión de servidor y FoxusWatch",
                color = FoxusTokens.SlateBlue
            )
            Icon(
                imageVector = if (showServerConfig) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = FoxusTokens.SlateBlue
            )
        }

        AnimatedVisibility(visible = showServerConfig) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Servidor FOXUS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = demoToken,
                    onValueChange = { demoToken = it },
                    label = { Text("Clave de acceso") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deviceToken,
                    onValueChange = { deviceToken = it },
                    label = { Text("Clave de enlace FoxusWatch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSaveConnection(baseUrl, demoToken, deviceToken) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxOrange,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Text("GUARDAR CONEXIÓN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
