package com.foxus.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxus.app.ui.components.FoxMascot
import com.foxus.app.ui.components.FoxMascotState
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.ChallengePhase
import com.foxus.app.viewmodel.FoxusUiState

@Composable
fun DeviceScreen(
    uiState: FoxusUiState,
    onRefreshDevices: () -> Unit,
    onPollNextCommand: () -> Unit,
    onSelectBleExperimental: (Boolean) -> Unit,
    onSimulateButton: (eventType: String) -> Unit
) {
    val activeDevice = uiState.devices.firstOrNull()
    var showHardwareSpecs by remember { mutableStateOf(false) }

    val watchMascotState = when (uiState.challengePhase) {
        ChallengePhase.RUNNING -> FoxMascotState.FOCUS
        ChallengePhase.VERIFYING_EVIDENCE -> FoxMascotState.CURIOUS
        ChallengePhase.COMPLETED_VICTORY -> FoxMascotState.VICTORY
        ChallengePhase.IDLE_READY -> if (uiState.activeMission != null) FoxMascotState.CHALLENGE else FoxMascotState.IDLE
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FoxMascot(state = watchMascotState, size = 46.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Watch,
                    contentDescription = null,
                    tint = FoxusTokens.FoxOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FoxusWatch",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FoxusTokens.WarmCream
                )
            }
            IconButton(onClick = onRefreshDevices) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Sincronizar FoxusWatch",
                    tint = FoxusTokens.WarmCream
                )
            }
        }

        // Connectivity Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !uiState.useBleExperimental,
                onClick = { onSelectBleExperimental(false) },
                label = { Text("Wi-Fi Directo") }
            )
            FilterChip(
                selected = uiState.useBleExperimental,
                onClick = { onSelectBleExperimental(true) },
                label = { Text("Bluetooth LE") }
            )
        }

        // Watch Face Preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A2418))
                .border(3.dp, Color(0xFF285943), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PANTALLA FOXUSWATCH",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF69C992)
                )
                Text(
                    text = if (activeDevice != null) "● Vinculado" else "○ En espera",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF69C992)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF143B27))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = uiState.lcdLine1.take(16).padEnd(16, ' '),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA8FFB8)
                    )
                    Text(
                        text = uiState.lcdLine2.take(16).padEnd(16, ' '),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA8FFB8)
                    )
                }
            }
        }

        // Wrist Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FoxusTokens.SurfaceCard)
                .border(1.dp, FoxusTokens.FoxOrange.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Controles de Muñeca",
                    style = MaterialTheme.typography.titleMedium,
                    color = FoxusTokens.FoxGold
                )
                TextButton(onClick = onPollNextCommand) {
                    Text("Sincronizar misión", color = FoxusTokens.FoxOrange)
                }
            }

            Button(
                onClick = { onSimulateButton("BUTTON_ACCEPT") },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FoxusTokens.FoxOrange,
                    contentColor = FoxusTokens.DarkSurface
                )
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ACEPTAR EN RELOJ (+10 XP)", fontWeight = FontWeight.ExtraBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onSimulateButton("BUTTON_POSTPONE") },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = FoxusTokens.WarmCream)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Posponer", color = FoxusTokens.WarmCream)
                }

                Button(
                    onClick = { onSimulateButton("BUTTON_COMPLETE") },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxGold,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Completar", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Collapsible technical details
        TextButton(
            onClick = { showHardwareSpecs = !showHardwareSpecs },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = if (showHardwareSpecs) "Ocultar ficha de FoxusWatch" else "Ficha de conectividad",
                color = FoxusTokens.SlateBlue
            )
            Icon(
                imageVector = if (showHardwareSpecs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = FoxusTokens.SlateBlue
            )
        }

        AnimatedVisibility(visible = showHardwareSpecs) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Dispositivo: ${activeDevice?.deviceName ?: "FoxusWatch"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxusTokens.WarmCream
                )
                Text(
                    text = "Canal activo: ${uiState.activeTransportName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxusTokens.TextSecondary
                )
            }
        }
    }
}
