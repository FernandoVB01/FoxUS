package com.foxus.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.FoxusUiState

/**
 * Rewards, Level Progression, Adaptive Learning Stats & Session History Screen (Section 11 & 13).
 * Explicitly labels user completions as self-reported without claiming external academic verification.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewardsScreen(
    uiState: FoxusUiState,
    onRefreshRewards: () -> Unit
) {
    val profile = uiState.rewardProfile
    val animatedTotalXp by animateIntAsState(
        targetValue = profile.totalXp,
        animationSpec = tween(durationMillis = 600),
        label = "rewards_total_xp"
    )
    val animatedRatio by animateFloatAsState(
        targetValue = profile.progressRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "rewards_progress_ratio"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progreso y Recompensas",
                style = MaterialTheme.typography.headlineMedium,
                color = FoxusTokens.WarmCream
            )
            IconButton(onClick = onRefreshRewards) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar recompensas",
                    tint = FoxusTokens.WarmCream
                )
            }
        }

        // Level & Deterministic XP Progress Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(FoxusTokens.DeepNavy)
                .border(1.dp, FoxusTokens.FoxGold.copy(alpha = 0.50f), RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = FoxusTokens.FoxGold
                    )
                    Text(
                        text = "  Nivel ${profile.level}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = FoxusTokens.FoxGold
                    )
                }
                Text(
                    text = "$animatedTotalXp XP",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = FoxusTokens.WarmCream
                )
            }

            LinearProgressIndicator(
                progress = { animatedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = FoxusTokens.FoxOrange,
                trackColor = FoxusTokens.DarkSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${profile.xpInCurrentLevel} / ${profile.xpForNextLevel} XP para nivel ${profile.level + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoxusTokens.TextSecondary
                )
                Text(
                    text = "${profile.completedMicrotasksCount} completadas · ${profile.startedSessionsCount} inicios",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoxusTokens.WarmCream
                )
            }
        }

        // Deterministic Rules Disclosure Card (Section 13)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FoxusTokens.SurfaceCard)
                .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Reglas Deterministas del Reward Engine",
                style = MaterialTheme.typography.titleMedium,
                color = FoxusTokens.FoxOrange
            )
            Text(
                text = "• +10 XP por inicio válido de sesión · +20 XP por microacción completada (autorreportada) · +10 XP por continuidad voluntaria.",
                style = MaterialTheme.typography.bodyMedium,
                color = FoxusTokens.TextSecondary
            )
            Text(
                text = "• Con clave idempotente única en PostgreSQL (nunca duplica XP ante reintentos).",
                style = MaterialTheme.typography.bodyMedium,
                color = FoxusTokens.TextSecondary
            )
        }

        // Achievements
        if (profile.achievements.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Logros Desbloqueados",
                    style = MaterialTheme.typography.titleMedium,
                    color = FoxusTokens.FoxGold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.achievements.forEach { badge ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(FoxusTokens.DeepNavy)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelLarge,
                                color = FoxusTokens.WarmCream
                            )
                        }
                    }
                }
            }
        }

        // Learning Agent Adaptive Hook Metrics
        if (profile.hookStats.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Aprendizaje Adaptativo por Hook (Learning Agent)",
                    style = MaterialTheme.typography.titleMedium,
                    color = FoxusTokens.FoxGold
                )
                profile.hookStats.forEach { stat ->
                    val label = if (stat.hookType == "fox_bzz") "FOX BZZ (Mosca)" else "FOX Challenge"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FoxusTokens.DarkSurface)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = FoxusTokens.WarmCream, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Vistos ${stat.shownCount} · Inicios ${stat.startedCount} · Éxitos ${stat.completedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoxusTokens.TextSecondary
                        )
                    }
                }
            }
        }

        // Recent Sessions History
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FoxusTokens.SurfaceCard)
                .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Historial Reciente de Sesiones",
                style = MaterialTheme.typography.titleMedium,
                color = FoxusTokens.FoxGold
            )
            if (uiState.recentSessions.isEmpty()) {
                Text(
                    text = "Aún no hay sesiones registradas. Inicia un reto o FOX BZZ para ver tu historial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoxusTokens.TextSecondary
                )
            } else {
                uiState.recentSessions.take(8).forEach { sess ->
                    val statusLabel = when (sess.status) {
                        "completed" -> "Completada (${sess.completionSource ?: "autorreportada"})"
                        "started" -> "En progreso"
                        else -> "Detenida / Pospuesta (${sess.completionSource ?: sess.status})"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FoxusTokens.DarkSurface)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (sess.status == "completed") {
                                    FoxusTokens.SuccessEmerald
                                } else {
                                    FoxusTokens.WarmCream
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sesión ${sess.id.take(8)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = FoxusTokens.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Recent XP Transactions
        if (uiState.rewardTransactions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Transacciones de XP (PostgreSQL)",
                    style = MaterialTheme.typography.titleMedium,
                    color = FoxusTokens.FoxOrange
                )
                uiState.rewardTransactions.take(10).forEach { tx ->
                    val reasonLabel = when (tx.rewardType) {
                        "session_start" -> "Inicio válido de microacción"
                        "microtask_complete" -> "Microacción completada (autorreportada)"
                        "voluntary_continuity" -> "Continuidad voluntaria extra"
                        else -> tx.rewardType
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = reasonLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoxusTokens.WarmCream
                        )
                        Text(
                            text = "+${tx.xpAmount} XP",
                            style = MaterialTheme.typography.labelLarge,
                            color = FoxusTokens.FoxGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
