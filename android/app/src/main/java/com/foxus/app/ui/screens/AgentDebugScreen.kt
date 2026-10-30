package com.foxus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.FoxusUiState

@Composable
fun AgentDebugScreen(uiState: FoxusUiState) {
    val trace = uiState.latestTrace

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Agent Debug · Gemma 4",
            style = MaterialTheme.typography.headlineMedium,
            color = FoxusTokens.WarmCream
        )

        if (trace == null) {
            Text(
                text = "Genera una misión desde Inicio o Tareas para inspeccionar aquí la secuencia real de agentes, modelos invocados y latencias.",
                style = MaterialTheme.typography.bodyLarge,
                color = FoxusTokens.TextSecondary
            )
            return
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FoxusTokens.DeepNavy)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Trace ID: ${trace.traceId}",
                style = MaterialTheme.typography.titleMedium,
                color = FoxusTokens.FoxGold
            )
            Text(
                text = "Ejecución real Gemma 4: ${if (trace.usedRealGemma) "SÍ" else "NO (Fallback determinista honesto)"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (trace.usedRealGemma) FoxusTokens.SuccessEmerald else FoxusTokens.WarningAmber
            )
            Text(
                text = "Latencia total pipeline: ${trace.totalDurationMs} ms",
                style = MaterialTheme.typography.bodyMedium,
                color = FoxusTokens.WarmCream
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(trace.steps, key = { it.id }) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(FoxusTokens.SurfaceCard)
                        .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = step.agentName,
                            fontWeight = FontWeight.Bold,
                            color = FoxusTokens.FoxOrange
                        )
                        Text(
                            text = "${step.durationMs ?: 0} ms · ${step.status}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoxusTokens.WarmCream
                        )
                    }
                    Text(
                        text = "Modelo: ${step.modelName ?: "reglas deterministas"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoxusTokens.TextSecondary
                    )
                    if (!step.outputSummary.isNullOrEmpty()) {
                        Text(
                            text = step.outputSummary.entries.joinToString(" | ") { "${it.key}=${it.value}" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoxusTokens.WarmCream
                        )
                    }
                }
            }
        }
    }
}
