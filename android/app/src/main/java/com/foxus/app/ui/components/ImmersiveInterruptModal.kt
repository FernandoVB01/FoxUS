package com.foxus.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foxus.app.data.remote.GenerateMissionResponseDto
import com.foxus.app.ui.theme.FoxusTokens
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Emergent Interruption Modal with embedded 30-second Ignition Mini-Games:
 * 1. "catch_fox" ("¡Atrapa al Zorrito!"): Tap darting FOX 3 times in 30s to ignite the challenge.
 * 2. "spark_combo" ("Chispas de Enfoque"): Tap numbered energy orbs 1->2->3->4 in 30s.
 * Also supports swipe-right to accept immediately or swipe-left to snooze consensually.
 */
@Composable
fun ImmersiveInterruptModal(
    mission: GenerateMissionResponseDto,
    urgencyReason: String,
    urgencyScore: Int,
    onPlayMinigameHit: (completed: Boolean) -> Unit,
    onAcceptStart: () -> Unit,
    onPostpone: () -> Unit,
    onSilence: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = dragOffsetX, label = "interrupt_swipe")

    var selectedGame by remember(mission.traceId) {
        mutableStateOf(
            if (mission.hook.ignitionMinigame == "spark_combo") "spark_combo" else "catch_fox"
        )
    }
    var secondsLeft by remember(mission.traceId, selectedGame) { mutableIntStateOf(30) }
    var foxSlot by remember { mutableIntStateOf(1) }
    var foxCatches by remember(mission.traceId, selectedGame) { mutableIntStateOf(0) }

    val sparkOrder = remember(mission.traceId, selectedGame) { listOf(1, 2, 3, 4).shuffled() }
    var nextSparkExpected by remember(mission.traceId, selectedGame) { mutableIntStateOf(1) }

    // 30-second mini-game countdown
    LaunchedEffect(mission.traceId, selectedGame) {
        secondsLeft = 30
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    // Darting FOX movement loop for "Atrapa al Zorrito"
    LaunchedEffect(selectedGame, foxCatches) {
        if (selectedGame == "catch_fox" && foxCatches < 3) {
            while (foxCatches < 3) {
                delay(760L)
                var candidate = Random.nextInt(0, 6)
                if (candidate == foxSlot) candidate = (candidate + 1) % 6
                foxSlot = candidate
            }
        }
    }

    val borderAccent = when {
        animatedOffsetX > 70f -> FoxusTokens.SuccessEmerald
        animatedOffsetX < -70f -> FoxusTokens.WarningAmber
        else -> FoxusTokens.FoxOrange
    }

    val bullets = remember(mission) {
        if (mission.microtask.steps.isNotEmpty()) {
            mission.microtask.steps.take(3)
        } else {
            listOf(mission.microtask.title, "Meta: ${mission.microtask.successCriteria}")
        }
    }

    Dialog(
        onDismissRequest = onPostpone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6061017))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                    .graphicsLayer {
                        rotationZ = (animatedOffsetX / 38f).coerceIn(-8f, 8f)
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragOffsetX > 180f -> {
                                        dragOffsetX = 0f
                                        onAcceptStart()
                                    }
                                    dragOffsetX < -180f -> {
                                        dragOffsetX = 0f
                                        onPostpone()
                                    }
                                    else -> dragOffsetX = 0f
                                }
                            },
                            onDragCancel = { dragOffsetX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-400f, 400f)
                            }
                        )
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(FoxusTokens.DeepNavy)
                    .border(2.dp, borderAccent, RoundedCornerShape(26.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(FoxusTokens.DarkSurface)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = urgencyReason.ifBlank { "🔥 INTERVENCIÓN ACTIVA · URGENCIA $urgencyScore/100" },
                        style = MaterialTheme.typography.labelLarge,
                        color = FoxusTokens.FoxGold,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = mission.microtask.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = FoxusTokens.WarmCream,
                    textAlign = TextAlign.Center
                )

                // Compact bullet preview
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FoxusTokens.SurfaceCard)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    bullets.forEach { step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", color = FoxusTokens.FoxOrange, fontWeight = FontWeight.ExtraBold)
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = FoxusTokens.WarmCream
                            )
                        }
                    }
                }

                // === 30-SECOND IGNITION MINI-GAME ARENA ===
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(FoxusTokens.DarkSurface)
                        .border(1.dp, FoxusTokens.FoxGold.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎮 MINIJUEGO DE ARRANQUE",
                            style = MaterialTheme.typography.labelLarge,
                            color = FoxusTokens.FoxGold
                        )
                        Text(
                            text = "${secondsLeft}s",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (secondsLeft <= 8) FoxusTokens.WarningAmber else FoxusTokens.FoxOrange,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (secondsLeft / 30f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = FoxusTokens.FoxOrange,
                        trackColor = FoxusTokens.DeepNavy
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedGame == "catch_fox",
                            onClick = { selectedGame = "catch_fox" },
                            label = { Text("🦊 Atrapa a FOX") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedGame == "spark_combo",
                            onClick = { selectedGame = "spark_combo" },
                            label = { Text("⚡ Chispas 1-4") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedGame == "catch_fox") {
                        Text(
                            text = "¡Toca al zorrito 3 veces para activar tu reto! ($foxCatches/3)",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.WarmCream,
                            textAlign = TextAlign.Center
                        )

                        // 2x3 Burrow Grid
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0 until 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (col in 0 until 3) {
                                        val idx = row * 3 + col
                                        val hasFox = idx == foxSlot
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (hasFox) FoxusTokens.SurfaceCard else FoxusTokens.DeepNavy
                                                )
                                                .border(
                                                    width = if (hasFox) 2.dp else 1.dp,
                                                    color = if (hasFox) FoxusTokens.FoxOrange else FoxusTokens.BorderSubtle,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    if (hasFox && foxCatches < 3) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val nextHits = foxCatches + 1
                                                        foxCatches = nextHits
                                                        foxSlot = (foxSlot + Random.nextInt(1, 5)) % 6
                                                        onPlayMinigameHit(nextHits >= 3)
                                                        if (nextHits >= 3) {
                                                            onAcceptStart()
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (hasFox) {
                                                FoxMascot(state = FoxMascotState.PLAYFUL, size = 54.dp)
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(FoxusTokens.DarkSurface)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Toca las chispas en orden: sigue el #${nextSparkExpected.coerceAtMost(4)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.WarmCream,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            sparkOrder.forEach { number ->
                                val alreadyTapped = number < nextSparkExpected
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                alreadyTapped -> FoxusTokens.SuccessEmerald
                                                number == nextSparkExpected -> FoxusTokens.FoxOrange
                                                else -> FoxusTokens.DeepNavy
                                            }
                                        )
                                        .border(2.dp, FoxusTokens.FoxGold, CircleShape)
                                        .clickable {
                                            if (number == nextSparkExpected) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val nextVal = nextSparkExpected + 1
                                                nextSparkExpected = nextVal
                                                onPlayMinigameHit(nextVal > 4)
                                                if (nextVal > 4) {
                                                    onAcceptStart()
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (alreadyTapped) "✓" else "$number",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (alreadyTapped || number == nextSparkExpected) {
                                            FoxusTokens.DarkSurface
                                        } else {
                                            FoxusTokens.WarmCream
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onAcceptStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoxusTokens.FoxOrange,
                        contentColor = FoxusTokens.DarkSurface
                    )
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("INICIAR DIRECTO (+10 XP)", fontWeight = FontWeight.ExtraBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPostpone,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = FoxusTokens.WarmCream)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Posponer", color = FoxusTokens.WarmCream)
                    }
                    TextButton(
                        onClick = onSilence,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Descansar", color = FoxusTokens.TextSecondary)
                    }
                }
            }
        }
    }
}
