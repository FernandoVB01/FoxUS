package com.foxus.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxus.app.data.local.CachedTaskEntity
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.FoxusUiState
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun TasksScreen(
    uiState: FoxusUiState,
    onCreateTask: (title: String, description: String?, needsPressure: Boolean, deadlineIso: String?) -> Unit,
    onToggleTaskPressure: (CachedTaskEntity) -> Unit,
    onGenerateMission: (taskId: String, title: String, mode: String) -> Unit,
    onOpenGoogleOAuth: ((String) -> Unit) -> Unit,
    onConnectGoogleIcal: (String) -> Unit,
    onConnectGoogleToken: (String, String?) -> Unit,
    onSyncGoogleNow: () -> Unit,
    onDisconnectGoogle: () -> Unit
) {
    val context = LocalContext.current
    var showCreateForm by remember { mutableStateOf(false) }
    var showGooglePanel by remember { mutableStateOf(false) }

    var titleInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var needsPressureInput by remember { mutableStateOf(true) }
    // 0 = Sin fecha, 1 = Urgente (4h), 2 = Mañana (24h)
    var deadlinePreset by remember { mutableIntStateOf(1) }

    var icalUrlInput by remember { mutableStateOf("") }
    var oauthTokenInput by remember { mutableStateOf("") }

    val expandedTaskIds = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Clean Top Bar with 2 Action Toggles (+ Tarea | Google Sync)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Proyectos y Retos",
                style = MaterialTheme.typography.headlineSmall,
                color = FoxusTokens.WarmCream,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        showGooglePanel = !showGooglePanel
                        if (showGooglePanel) showCreateForm = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.CloudSync,
                        contentDescription = null,
                        tint = if (uiState.googleStatus.connected) FoxusTokens.SuccessEmerald else FoxusTokens.FoxGold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.googleStatus.connected) "Google ✓" else "Google",
                        color = FoxusTokens.WarmCream
                    )
                }

                Button(
                    onClick = {
                        showCreateForm = !showCreateForm
                        if (showCreateForm) showGooglePanel = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxOrange,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Nueva", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Collapsible Google Tasks & Google Calendar Drawer
        AnimatedVisibility(visible = showGooglePanel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoxusTokens.DeepNavy)
                    .border(1.dp, FoxusTokens.FoxGold.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Google Tasks + Google Calendar",
                    style = MaterialTheme.typography.titleMedium,
                    color = FoxusTokens.FoxGold
                )

                if (uiState.googleStatus.connected) {
                    Text(
                        text = "• Conectado (${uiState.googleStatus.connectedEmail ?: uiState.googleStatus.authMode})\n" +
                            "• ${uiState.googleStatus.syncedGoogleTasksCount} tareas y ${uiState.googleStatus.upcomingEventsCount} eventos próximos",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoxusTokens.SuccessEmerald
                    )
                    uiState.googleStatus.upcomingEvents.take(3).forEach { ev ->
                        Text(
                            text = "📅 ${ev.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.WarmCream
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSyncGoogleNow,
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FoxusTokens.FoxOrange,
                                contentColor = FoxusTokens.DarkSurface
                            )
                        ) {
                            Text("Sincronizar ahora", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onDisconnectGoogle) {
                            Text("Desconectar", color = FoxusTokens.TextSecondary)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            onOpenGoogleOAuth { authUrl ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FoxusTokens.FoxOrange,
                            contentColor = FoxusTokens.DarkSurface
                        )
                    ) {
                        Text("1. Iniciar sesión con Google (OAuth 2.0)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = icalUrlInput,
                        onValueChange = { icalUrlInput = it },
                        label = { Text("Opción rápida Calendar: URL secreta iCal (.ics)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (icalUrlInput.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                onConnectGoogleIcal(icalUrlInput)
                                icalUrlInput = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Conectar Google Calendar (.ics)", color = FoxusTokens.FoxGold)
                        }
                    }

                    OutlinedTextField(
                        value = oauthTokenInput,
                        onValueChange = { oauthTokenInput = it },
                        label = { Text("Opción Token directo (Tasks + Calendar API)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (oauthTokenInput.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                onConnectGoogleToken(oauthTokenInput, icalUrlInput.ifBlank { null })
                                oauthTokenInput = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sincronizar Google Tasks + Calendar", color = FoxusTokens.SuccessEmerald)
                        }
                    }
                }
            }
        }

        // Collapsible Task Creation Drawer (With Urgency + "Necesito presión" consent!)
        AnimatedVisibility(visible = showCreateForm) {
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
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Proyecto o tarea (Ej: 12 diapositivas)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descInput,
                    onValueChange = { descInput = it },
                    label = { Text("Nota breve opcional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = deadlinePreset == 1,
                        onClick = { deadlinePreset = 1 },
                        label = { Text("⏰ Urgente (4h)") }
                    )
                    FilterChip(
                        selected = deadlinePreset == 2,
                        onClick = { deadlinePreset = 2 },
                        label = { Text("📅 Mañana") }
                    )
                    FilterChip(
                        selected = deadlinePreset == 0,
                        onClick = { deadlinePreset = 0 },
                        label = { Text("Libre") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 Admito que necesito presión de FOX",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoxusTokens.WarmCream
                    )
                    Switch(
                        checked = needsPressureInput,
                        onCheckedChange = { needsPressureInput = it }
                    )
                }

                Button(
                    onClick = {
                        if (titleInput.isNotBlank()) {
                            val deadlineIso = when (deadlinePreset) {
                                1 -> Instant.now().plus(4, ChronoUnit.HOURS).toString()
                                2 -> Instant.now().plus(24, ChronoUnit.HOURS).toString()
                                else -> null
                            }
                            onCreateTask(
                                titleInput,
                                descInput.ifBlank { null },
                                needsPressureInput,
                                deadlineIso
                            )
                            titleInput = ""
                            descInput = ""
                            showCreateForm = false
                        }
                    },
                    enabled = titleInput.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxOrange,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Text("GUARDAR PROYECTO", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Clean, Uncluttered Task Cards with Expandable Details
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.tasks, key = { it.id }) { task ->
                val isExpanded = expandedTaskIds[task.id] == true
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(FoxusTokens.SurfaceCard)
                        .border(
                            1.dp,
                            if (task.needsPressure) FoxusTokens.FoxOrange.copy(alpha = 0.6f)
                            else FoxusTokens.BorderSubtle,
                            RoundedCornerShape(18.dp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = FoxusTokens.WarmCream
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                // One-tap toggle for "Necesito presión"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (task.needsPressure) FoxusTokens.FoxOrange.copy(alpha = 0.22f)
                                            else FoxusTokens.DeepNavy
                                        )
                                        .clickable { onToggleTaskPressure(task) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.LocalFireDepartment,
                                            contentDescription = null,
                                            tint = if (task.needsPressure) FoxusTokens.FoxOrange else FoxusTokens.TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (task.needsPressure) "Con presión" else "Sin presión",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (task.needsPressure) FoxusTokens.FoxGold else FoxusTokens.TextSecondary
                                        )
                                    }
                                }

                                if (task.source == "google_tasks") {
                                    Text(
                                        text = "• Google Tasks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FoxusTokens.SuccessEmerald
                                    )
                                }
                            }
                        }

                        if (!task.description.isNullOrBlank() || !task.latestMicrotaskTitle.isNullOrBlank()) {
                            TextButton(onClick = { expandedTaskIds[task.id] = !isExpanded }) {
                                Text(
                                    text = if (isExpanded) "Menos" else "Info",
                                    color = FoxusTokens.SlateBlue
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = FoxusTokens.SlateBlue
                                )
                            }
                        }
                    }

                    // Expandable extra details ONLY when tapped!
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FoxusTokens.DeepNavy)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (!task.description.isNullOrBlank()) {
                                Text(
                                    text = "• ${task.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FoxusTokens.WarmCream
                                )
                            }
                            if (!task.latestMicrotaskTitle.isNullOrBlank()) {
                                Text(
                                    text = "• Último reto: ${task.latestMicrotaskTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FoxusTokens.FoxGold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onGenerateMission(task.id, task.title, "challenge") },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FoxusTokens.FoxOrange,
                                contentColor = FoxusTokens.DarkSurface
                            )
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reto 3m", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onGenerateMission(task.id, task.title, "fox_bzz") },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🪰 BZZ", color = FoxusTokens.FoxGold)
                        }
                    }
                }
            }
        }
    }
}
