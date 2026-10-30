package com.foxus.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foxus.app.ui.components.FoxMascot
import com.foxus.app.ui.components.FoxMascotState
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.FoxusUiState
import com.foxus.app.viewmodel.resolveBulletSteps
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mode 2 — FOX BZZ ("La Mosca Traviesa") (Section 6 & Section 11).
 * Playful, non-intrusive virtual fly inside FOXUS UI:
 * - Never blocks controls or phone usage.
 * - Never loops sounds infinitely.
 * - Requires zero accessibility or global overlay permissions in MVP.
 * - Uses the exact same multi-agent Gemma 4 pipeline, PostgreSQL persistence, and Reward Engine.
 */
@Composable
fun FoxBzzScreen(
    uiState: FoxusUiState,
    onAcceptBzzMission: () -> Unit,
    onPostponeBzz: () -> Unit,
    onSilenceFly: () -> Unit,
    onReplayDiscreetBuzz: () -> Unit,
    onGenerateNewBzz: (taskId: String, title: String) -> Unit,
    onPickTaskFirst: () -> Unit,
    onOpenTraces: () -> Unit
) {
    val mission = uiState.activeMission
    val primaryTask = uiState.tasks.firstOrNull { it.status != "completed" } ?: uiState.tasks.firstOrNull()
    val isSleeping = uiState.settings.interventionLevel <= 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header + Discreet Single-Shot Buzz Preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FOX BZZ · La Mosca Traviesa",
                    style = MaterialTheme.typography.titleLarge,
                    color = FoxusTokens.FoxGold
                )
                Text(
                    text = "Intervención juguetona no intrusiva dentro de FOXUS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoxusTokens.TextSecondary
                )
            }
            IconButton(onClick = onReplayDiscreetBuzz) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Escuchar zumbido discreto una vez",
                    tint = FoxusTokens.FoxGold
                )
            }
        }

        // Playful Stage: Lissajous Virtual Fly + Reactive Geometric FOX Mascot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(FoxusTokens.DeepNavy)
                .border(1.dp, FoxusTokens.FoxGold.copy(alpha = 0.40f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (!isSleeping) {
                PlayfulFlyCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                FoxMascot(
                    state = if (isSleeping) FoxMascotState.SLEEP else FoxMascotState.PLAYFUL,
                    size = 138.dp
                )
            }
        }

        if (mission == null || mission.hook.hookType != "fox_bzz") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (primaryTask != null) {
                        "¿Probamos el modo travieso con \"${primaryTask.title}\"?"
                    } else {
                        "Crea o elige una tarea para despertar a la Mosca Traviesa"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = FoxusTokens.WarmCream,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Hook Agent pedirá a Google Gemma 4 un guiño breve para romper el hielo sin presión.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoxusTokens.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        if (primaryTask != null) {
                            onGenerateNewBzz(primaryTask.id, primaryTask.title)
                        } else {
                            onPickTaskFirst()
                        }
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxGold,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Text(
                        text = if (primaryTask != null) "INVOCAR FOX BZZ CON GEMMA 4" else "IR A TAREAS",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            return
        }

        // Honest AI Provenance Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GUIÑO CONTEXTUAL",
                style = MaterialTheme.typography.labelLarge,
                color = FoxusTokens.FoxOrange
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (mission.isSimulatedAi) {
                        "Respaldo: ${mission.hook.modelName}"
                    } else {
                        "IA Real: ${mission.hook.modelName}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mission.isSimulatedAi) FoxusTokens.WarningAmber else FoxusTokens.SuccessEmerald
                )
            }
        }

        // Speech Bubble Card with Short Quote + Crisp Bullets (Expandable Details!)
        var showDetails by remember { mutableStateOf(false) }
        val bullets = remember(mission.traceId) { resolveBulletSteps(mission) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FoxusTokens.SurfaceCard)
                .border(1.dp, FoxusTokens.FoxGold.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = mission.hook.message,
                style = MaterialTheme.typography.titleMedium,
                color = FoxusTokens.WarmCream
            )
            bullets.take(3).forEach { step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("• ", color = FoxusTokens.FoxGold, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoxusTokens.WarmCream
                    )
                }
            }
            TextButton(
                onClick = { showDetails = !showDetails },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (showDetails) "Menos" else "Detalles IA", color = FoxusTokens.SlateBlue)
                Icon(
                    imageVector = if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = FoxusTokens.SlateBlue
                )
            }
            AnimatedVisibility(visible = showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "• Modelo: ${mission.hook.modelName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoxusTokens.SuccessEmerald
                    )
                    TextButton(onClick = onOpenTraces) {
                        Text("Ver traza ${mission.traceId} ➔", color = FoxusTokens.FoxOrange)
                    }
                }
            }
        }

        // Non-Blocking Action Controls: Accept (+10 XP), Postpone, or Silence Fly
        Button(
            onClick = onAcceptBzzMission,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FoxusTokens.FoxOrange,
                contentColor = FoxusTokens.DarkSurface
            )
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ACEPTAR RETO Y EMPEZAR (+10 XP)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPostponeBzz,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = FoxusTokens.WarmCream)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Posponer", color = FoxusTokens.WarmCream)
            }

            OutlinedButton(
                onClick = onSilenceFly,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null,
                    tint = FoxusTokens.SlateBlue
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Silenciar", color = FoxusTokens.TextSecondary)
            }
        }

        TextButton(onClick = onOpenTraces) {
            Text(
                text = "Ver traza de Gemma 4 (${mission.traceId})",
                color = FoxusTokens.SlateBlue
            )
        }
    }
}

@Composable
private fun PlayfulFlyCanvas(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "fox_bzz_fly")
    val orbitPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fly_orbit"
    )
    val wingFlutter by infinite.animateFloat(
        initialValue = -22f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wing_flutter"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Lissajous figure-8 trajectory confined to the upper stage so FOX remains clear below
        val flyX = w * (0.50f + 0.36f * sin(orbitPhase.toDouble()).toFloat())
        val flyY = h * (0.28f + 0.16f * sin(2.0 * orbitPhase.toDouble()).toFloat())

        // Subtle dotted golden flight trail behind the fly
        for (step in 1..6) {
            val trailPhase = orbitPhase - step * 0.22f
            val tx = w * (0.50f + 0.36f * sin(trailPhase.toDouble()).toFloat())
            val ty = h * (0.28f + 0.16f * sin(2.0 * trailPhase.toDouble()).toFloat())
            drawCircle(
                color = FoxusTokens.FoxGold.copy(alpha = (0.36f - step * 0.05f).coerceAtLeast(0.05f)),
                radius = (4.5f - step * 0.5f).dp.toPx(),
                center = Offset(tx, ty)
            )
        }

        // Dashed orbit hint ring
        drawCircle(
            color = FoxusTokens.SlateBlue.copy(alpha = 0.22f),
            radius = 22.dp.toPx(),
            center = Offset(flyX, flyY),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        )

        val headingDeg = (cos(orbitPhase.toDouble()) * 18.0).toFloat()
        rotate(degrees = headingDeg, pivot = Offset(flyX, flyY)) {
            // Left translucent wing
            rotate(degrees = wingFlutter, pivot = Offset(flyX - 4.dp.toPx(), flyY - 3.dp.toPx())) {
                drawOval(
                    color = FoxusTokens.WarmCream.copy(alpha = 0.78f),
                    topLeft = Offset(flyX - 18.dp.toPx(), flyY - 14.dp.toPx()),
                    size = Size(15.dp.toPx(), 9.dp.toPx())
                )
            }
            // Right translucent wing
            rotate(degrees = -wingFlutter, pivot = Offset(flyX + 4.dp.toPx(), flyY - 3.dp.toPx())) {
                drawOval(
                    color = FoxusTokens.WarmCream.copy(alpha = 0.78f),
                    topLeft = Offset(flyX + 3.dp.toPx(), flyY - 14.dp.toPx()),
                    size = Size(15.dp.toPx(), 9.dp.toPx())
                )
            }
            // Geometric body + FoxGold stripe
            drawOval(
                color = Color(0xFF1A2A34),
                topLeft = Offset(flyX - 7.dp.toPx(), flyY - 5.dp.toPx()),
                size = Size(14.dp.toPx(), 11.dp.toPx())
            )
            drawOval(
                color = FoxusTokens.FoxGold,
                topLeft = Offset(flyX - 4.dp.toPx(), flyY - 2.dp.toPx()),
                size = Size(8.dp.toPx(), 4.dp.toPx())
            )
            // Bright eyes
            drawCircle(
                color = FoxusTokens.FoxOrange,
                radius = 2.2.dp.toPx(),
                center = Offset(flyX - 3.dp.toPx(), flyY - 3.dp.toPx())
            )
            drawCircle(
                color = FoxusTokens.FoxOrange,
                radius = 2.2.dp.toPx(),
                center = Offset(flyX + 3.dp.toPx(), flyY - 3.dp.toPx())
            )
        }
    }
}
