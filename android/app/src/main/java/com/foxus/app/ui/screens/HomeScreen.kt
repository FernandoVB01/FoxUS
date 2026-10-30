package com.foxus.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxus.app.R
import com.foxus.app.ui.components.FoxMascot
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.FoxusUiState
import com.foxus.app.viewmodel.resolveBulletSteps
import com.foxus.app.viewmodel.resolveHomeMascotState

@Composable
fun HomeScreen(
    uiState: FoxusUiState,
    onLaunchChallengeForTask: (taskId: String, title: String, mode: String) -> Unit,
    onGoToTasks: () -> Unit,
    onGoToActiveMission: () -> Unit,
    onGoToRewards: () -> Unit,
    onGoToTraces: () -> Unit,
    onGoToSettings: () -> Unit,
    onTriggerImmersiveNow: () -> Unit,
    onScheduleInterrupt5s: () -> Unit,
    onUpdateSmartPressure: (scheduled: Boolean, overlayOptIn: Boolean) -> Unit,
    onRefreshOverlayPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var showPressureDrawer by remember { mutableStateOf(false) }
    var showAiDetails by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onScheduleInterrupt5s()
    }

    val overlayPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshOverlayPermission()
        if (Settings.canDrawOverlays(context)) {
            onUpdateSmartPressure(uiState.settings.smartPressureScheduled, true)
        }
    }

    val mascotState = resolveHomeMascotState(
        interventionLevel = uiState.settings.interventionLevel,
        hasActiveMission = uiState.activeMission != null || uiState.tasks.isNotEmpty(),
        hookType = uiState.activeMission?.hook?.hookType
    )

    val primaryTask = uiState.tasks.firstOrNull { it.status != "completed" } ?: uiState.tasks.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Compact Header + Integrated XP Pill (Minimal, zero clutter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.foxus_logo_official),
                    contentDescription = "FOXUS",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.clickable(onClick = onGoToRewards)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Nv. ${uiState.rewardProfile.level} · ${uiState.rewardProfile.totalXp} XP",
                            style = MaterialTheme.typography.titleMedium,
                            color = FoxusTokens.FoxGold,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.backendOnline) FoxusTokens.SuccessEmerald
                                    else FoxusTokens.WarningAmber
                                )
                        )
                    }
                    LinearProgressIndicator(
                        progress = { uiState.rewardProfile.progressRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(120.dp)
                            .padding(top = 4.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = FoxusTokens.FoxOrange,
                        trackColor = FoxusTokens.DeepNavy
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = FoxusTokens.WarmCream)
                }
                IconButton(onClick = onGoToSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = FoxusTokens.WarmCream)
                }
            }
        }

        if (uiState.googleStatus.tokenExpired) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FoxusTokens.DeepNavy)
                    .border(1.dp, FoxusTokens.WarningAmber, RoundedCornerShape(14.dp))
                    .clickable(onClick = onGoToTasks)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚠️ Tu token temporal de Google venció (1h). Toca aquí para renovarlo y traer tus tareas nuevas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxusTokens.WarningAmber,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. Clean Protagonist Stage
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FoxMascot(state = mascotState, size = 148.dp)
            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(6.dp))
                CircularProgressIndicator(
                    color = FoxusTokens.FoxOrange,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
            }
        }

        // 3. Focused Action Card (Short title + Bullets only, details collapsible!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(FoxusTokens.SurfaceCard)
                .border(1.dp, FoxusTokens.FoxOrange.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val activeMission = uiState.activeMission
            if (activeMission != null) {
                val bullets = remember(activeMission) { resolveBulletSteps(activeMission) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeMission.hook.visualModeName.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = FoxusTokens.FoxGold
                    )
                    Text(
                        text = "${activeMission.microtask.estimatedSeconds / 60} min",
                        style = MaterialTheme.typography.labelLarge,
                        color = FoxusTokens.FoxOrange
                    )
                }
                Text(
                    text = activeMission.microtask.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = FoxusTokens.WarmCream
                )

                // Crisp Bullet Points instead of dense paragraphs
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    bullets.take(3).forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", color = FoxusTokens.FoxOrange, fontWeight = FontWeight.ExtraBold)
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = FoxusTokens.WarmCream
                            )
                        }
                    }
                }

                Button(
                    onClick = onGoToActiveMission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxOrange,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Text("CONTINUAR RETO", fontWeight = FontWeight.ExtraBold)
                }
            } else if (primaryTask != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (primaryTask.needsPressure) "🔥 PROYECTO CON PRESIÓN" else "SIGUIENTE PASO",
                        style = MaterialTheme.typography.labelLarge,
                        color = FoxusTokens.FoxGold
                    )
                    if (primaryTask.source == "google_tasks") {
                        Text(
                            text = "Google Tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.SuccessEmerald
                        )
                    }
                }
                Text(
                    text = primaryTask.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = FoxusTokens.WarmCream
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onLaunchChallengeForTask(primaryTask.id, primaryTask.title, "challenge")
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FoxusTokens.FoxOrange,
                            contentColor = FoxusTokens.DarkSurface
                        )
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reto 3 min", fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick = {
                            onLaunchChallengeForTask(primaryTask.id, primaryTask.title, "fox_bzz")
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, tint = FoxusTokens.FoxGold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Modo BZZ", color = FoxusTokens.WarmCream)
                    }
                }
            } else {
                Text(
                    text = "Agrega una tarea para empezar",
                    style = MaterialTheme.typography.titleLarge,
                    color = FoxusTokens.WarmCream
                )
                Button(
                    onClick = onGoToTasks,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxOrange,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Text("+ NUEVA TAREA O GOOGLE TASKS", fontWeight = FontWeight.Bold)
                }
            }

            // Expandable AI & Calendar Context (Hidden until tapped!)
            TextButton(
                onClick = { showAiDetails = !showAiDetails },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = if (showAiDetails) "Ocultar detalles" else "Ver qué detectó FOX",
                    color = FoxusTokens.SlateBlue
                )
                Icon(
                    imageVector = if (showAiDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = FoxusTokens.SlateBlue
                )
            }

            AnimatedVisibility(visible = showAiDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FoxusTokens.DeepNavy)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "• Motor IA: ${uiState.aiModeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoxusTokens.WarmCream
                    )
                    if (uiState.googleStatus.connected) {
                        Text(
                            text = "• Google: ${uiState.googleStatus.upcomingEventsCount} eventos próximos y ${uiState.googleStatus.syncedGoogleTasksCount} tareas",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.SuccessEmerald
                        )
                    } else {
                        Text(
                            text = "• Google Calendar/Tasks: Conéctalo en la pestaña Tareas",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.TextSecondary
                        )
                    }
                    if (activeMission != null) {
                        Text(
                            text = "• Mensaje FOX: \"${activeMission.hook.message}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.FoxGold
                        )
                    }
                    TextButton(onClick = onGoToTraces) {
                        Text("Abrir trazabilidad de agentes Gemma 4 ➔", color = FoxusTokens.FoxOrange)
                    }
                }
            }
        }

        // 4. Collapsible Duolingo-Style Smart Pressure & Interruption Drawer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FoxusTokens.DeepNavy)
                .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(18.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPressureDrawer = !showPressureDrawer }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = FoxusTokens.FoxGold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Alertas Emergentes y Minijuegos",
                        style = MaterialTheme.typography.titleSmall,
                        color = FoxusTokens.WarmCream,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (showPressureDrawer) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = FoxusTokens.FoxGold
                )
            }

            AnimatedVisibility(visible = showPressureDrawer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTriggerImmersiveNow,
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FoxusTokens.FoxGold,
                                contentColor = FoxusTokens.DarkSurface
                            )
                        ) {
                            Text("🎮 Minijuego 30s", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onScheduleInterrupt5s()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔔 Alerta Emergente", color = FoxusTokens.WarmCream)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Presión programada inteligente",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FoxusTokens.WarmCream
                            )
                            Text(
                                text = "Aprende e interrumpe en entregas urgentes",
                                style = MaterialTheme.typography.bodySmall,
                                color = FoxusTokens.TextSecondary
                            )
                        }
                        Switch(
                            checked = uiState.settings.smartPressureScheduled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onUpdateSmartPressure(enabled, uiState.settings.experimentalOverlayEnabled)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Superponer sobre otras apps (Opcional)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FoxusTokens.WarmCream
                            )
                            Text(
                                text = if (uiState.overlayPermissionGranted) {
                                    "Permiso concedido · Tarjeta deslizable activa"
                                } else {
                                    "Permiso avanzado solo si aceptas presión extra"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.overlayPermissionGranted) {
                                    FoxusTokens.SuccessEmerald
                                } else {
                                    FoxusTokens.TextSecondary
                                }
                            )
                        }
                        Switch(
                            checked = uiState.settings.experimentalOverlayEnabled && uiState.overlayPermissionGranted,
                            onCheckedChange = { wantOverlay ->
                                if (wantOverlay && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayPermLauncher.launch(intent)
                                } else {
                                    onUpdateSmartPressure(uiState.settings.smartPressureScheduled, wantOverlay)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
