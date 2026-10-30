package com.foxus.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.foxus.app.ui.components.FoxCelebrationBurst
import com.foxus.app.ui.components.FoxMascot
import com.foxus.app.ui.components.FoxMascotState
import com.foxus.app.ui.theme.FoxusTokens
import com.foxus.app.viewmodel.ChallengePhase
import com.foxus.app.viewmodel.FoxusUiState
import com.foxus.app.viewmodel.formatCountdownClock
import com.foxus.app.viewmodel.resolveBulletSteps
import java.io.ByteArrayOutputStream
import java.util.Locale

private fun encodeBitmapToBase64Jpeg(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

@Composable
fun ChallengeScreen(
    uiState: FoxusUiState,
    onStartTimer: () -> Unit,
    onBulletCheckedCheer: (checkedCount: Int, totalCount: Int) -> Unit,
    onTriggerPepTalk: () -> Unit,
    onToggleFocusMusic: () -> Unit,
    onToggleVoiceCoach: () -> Unit,
    onOpenEvidenceStage: () -> Unit,
    onSubmitPhotoEvidence: (jpegBase64: String, checkedCount: Int) -> Unit,
    onSubmitVoiceEvidence: (voiceTranscript: String, checkedCount: Int) -> Unit,
    onCompleteChallengeWithoutEvidence: (voluntaryContinuity: Boolean) -> Unit,
    onPostpone: () -> Unit,
    onOpenTraces: () -> Unit,
    onOpenProfile: () -> Unit,
    onPickTaskFirst: () -> Unit
) {
    val mission = uiState.activeMission
    var showTechDetails by remember { mutableStateOf(false) }

    if (mission == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FoxusTokens.DarkSurface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FoxMascot(state = FoxMascotState.CURIOUS, size = 148.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Selecciona una tarea para iniciar tu reto guiado",
                style = MaterialTheme.typography.titleLarge,
                color = FoxusTokens.WarmCream,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onPickTaskFirst,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FoxusTokens.FoxOrange,
                    contentColor = FoxusTokens.DarkSurface
                )
            ) {
                Text("ELEGIR TAREA", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val bullets = remember(mission.traceId) { resolveBulletSteps(mission) }
    val checkedIndices = remember(mission.traceId) { mutableStateListOf<Int>() }

    var capturedPhoto by remember(mission.traceId) { mutableStateOf<Bitmap?>(null) }
    var voiceNoteText by remember(mission.traceId) { mutableStateOf("") }
    var showVoiceEditor by remember(mission.traceId) { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedPhoto = bitmap
            val encoded = encodeBitmapToBase64Jpeg(bitmap)
            onSubmitPhotoEvidence(encoded, checkedIndices.size)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        showVoiceEditor = true
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                voiceNoteText = spoken
                onSubmitVoiceEvidence(spoken, checkedIndices.size)
            }
        }
    }

    val mascotState = when (uiState.challengePhase) {
        ChallengePhase.IDLE_READY -> {
            if (mission.hook.hookType == "fox_bzz") FoxMascotState.PLAYFUL else FoxMascotState.CHALLENGE
        }
        ChallengePhase.RUNNING -> FoxMascotState.FOCUS
        ChallengePhase.VERIFYING_EVIDENCE -> FoxMascotState.CURIOUS
        ChallengePhase.COMPLETED_VICTORY -> FoxMascotState.VICTORY
    }

    val rawProgress = if (uiState.totalTimerSeconds > 0) {
        uiState.remainingTimerSeconds.toFloat() / uiState.totalTimerSeconds.toFloat()
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "timer_arc_progress"
    )
    val animatedEarnedXp by animateIntAsState(
        targetValue = uiState.lastEarnedXp,
        animationSpec = tween(durationMillis = 650),
        label = "victory_xp_counter"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FoxusTokens.DarkSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mission.microtask.title,
                style = MaterialTheme.typography.titleLarge,
                color = FoxusTokens.WarmCream,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(FoxusTokens.DeepNavy)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (mission.isSimulatedAi) "Local" else "Gemma 4",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mission.isSimulatedAi) FoxusTokens.WarningAmber else FoxusTokens.SuccessEmerald
                )
            }
        }

        // Circular Timer + Mascot
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = FoxusTokens.DeepNavy,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    color = when (uiState.challengePhase) {
                        ChallengePhase.COMPLETED_VICTORY -> FoxusTokens.FoxGold
                        ChallengePhase.VERIFYING_EVIDENCE -> FoxusTokens.SuccessEmerald
                        else -> FoxusTokens.FoxOrange
                    },
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = stroke
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FoxMascot(state = mascotState, size = 108.dp)
                Text(
                    text = if (mission.timerMode == "stopwatch") "Cronómetro · máx. ${uiState.totalTimerSeconds / 60} min" else "Tiempo restante",
                    style = MaterialTheme.typography.labelSmall,
                    color = FoxusTokens.WarmCream
                )
                Text(
                    text = formatCountdownClock(if (mission.timerMode == "stopwatch") uiState.elapsedSessionSeconds else uiState.remainingTimerSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = FoxusTokens.WarmCream
                )
            }
        }

        // Live Referee / Coach Voice Banner + Music & Voice Quick Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FoxusTokens.DeepNavy)
                .border(1.dp, FoxusTokens.FoxGold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = FoxusTokens.FoxGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ÁRBITRO FOX EN VIVO",
                    style = MaterialTheme.typography.labelLarge,
                    color = FoxusTokens.FoxGold
                )
            }
            Text(
                text = "“${uiState.coachLiveMessage}”",
                style = MaterialTheme.typography.bodyMedium,
                color = FoxusTokens.WarmCream
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.settings.musicEnabled,
                    onClick = onToggleFocusMusic,
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.settings.musicEnabled) {
                                Icons.Filled.MusicNote
                            } else {
                                Icons.Filled.MusicOff
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Música") }
                )
                FilterChip(
                    selected = uiState.settings.voiceCoachEnabled,
                    onClick = onToggleVoiceCoach,
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.settings.voiceCoachEnabled) {
                                Icons.Filled.RecordVoiceOver
                            } else {
                                Icons.Filled.VoiceOverOff
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Voz") }
                )
                OutlinedButton(
                    onClick = onTriggerPepTalk,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📣 ¡Ánimo!", color = FoxusTokens.FoxGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Checkable Bullet Points
        if (uiState.challengePhase != ChallengePhase.VERIFYING_EVIDENCE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .border(1.dp, FoxusTokens.BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "OBJETIVOS DEL RETO (${checkedIndices.size}/${bullets.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = FoxusTokens.FoxGold
                )

                bullets.forEachIndexed { idx, stepText ->
                    val done = checkedIndices.contains(idx)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (done) FoxusTokens.DeepNavy else FoxusTokens.DarkSurface)
                            .clickable {
                                if (done) {
                                    checkedIndices.remove(idx)
                                } else {
                                    checkedIndices.add(idx)
                                    onBulletCheckedCheer(checkedIndices.size, bullets.size)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) FoxusTokens.FoxGold else FoxusTokens.FoxOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stepText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (done) FoxusTokens.TextSecondary else FoxusTokens.WarmCream,
                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                }
            }
        }

        // Phase Controls
        AnimatedContent(
            targetState = uiState.challengePhase,
            label = "challenge_phase_controls"
        ) { phase ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (phase) {
                    ChallengePhase.IDLE_READY -> {
                        Button(
                            onClick = onStartTimer,
                            enabled = !uiState.isActionBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FoxusTokens.FoxOrange,
                                contentColor = FoxusTokens.DarkSurface
                            )
                        ) {
                            Text(
                                text = "EMPEZAR",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        OutlinedButton(
                            onClick = onPostpone,
                            enabled = !uiState.isActionBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Posponer", color = FoxusTokens.TextSecondary)
                        }
                    }

                    ChallengePhase.RUNNING -> {
                        Button(
                            onClick = onOpenEvidenceStage,
                            enabled = !uiState.isActionBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FoxusTokens.FoxGold,
                                contentColor = FoxusTokens.DarkSurface
                            )
                        ) {
                            Text(
                                text = "MOSTRAR MI AVANCE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        OutlinedButton(
                            onClick = onPostpone,
                            enabled = !uiState.isActionBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Pausar / Posponer", color = FoxusTokens.TextSecondary)
                        }
                    }

                    ChallengePhase.VERIFYING_EVIDENCE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(FoxusTokens.SurfaceCard)
                                .border(1.5.dp, FoxusTokens.FoxOrange, RoundedCornerShape(20.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "TU AVANCE",
                                style = MaterialTheme.typography.labelLarge,
                                color = FoxusTokens.FoxGold
                            )
                            Text(
                                text = "Una foto o tu voz. Tú eliges.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FoxusTokens.WarmCream,
                                textAlign = TextAlign.Center
                            )

                            val bmp = capturedPhoto
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Foto capturada",
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, FoxusTokens.FoxGold, RoundedCornerShape(14.dp))
                                )
                            }

                            if (uiState.isVerifyingEvidence) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = FoxusTokens.FoxOrange,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Text(
                                        text = "Revisando tu avance…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FoxusTokens.FoxGold
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { cameraLauncher.launch() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = FoxusTokens.FoxOrange,
                                            contentColor = FoxusTokens.DarkSurface
                                        )
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Foto", fontWeight = FontWeight.ExtraBold)
                                    }

                                    Button(
                                        onClick = {
                                            showVoiceEditor = true
                                            runCatching {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(
                                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                    )
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "EC").toLanguageTag())
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Cuéntale a FOX qué lograste hacer...")
                                                }
                                                speechLauncher.launch(intent)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = FoxusTokens.FoxGold,
                                            contentColor = FoxusTokens.DarkSurface
                                        )
                                    ) {
                                        Icon(Icons.Filled.Mic, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Voz", fontWeight = FontWeight.ExtraBold)
                                    }
                                }

                                AnimatedVisibility(visible = showVoiceEditor) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = voiceNoteText,
                                            onValueChange = { voiceNoteText = it },
                                            label = { Text("Transcripción de tu reporte de voz") },
                                            placeholder = { Text("Ej: Abrí la presentación y creé la portada con el título...") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Button(
                                            onClick = {
                                                onSubmitVoiceEvidence(voiceNoteText, checkedIndices.size)
                                            },
                                            enabled = voiceNoteText.trim().length >= 5,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = FoxusTokens.SuccessEmerald,
                                                contentColor = FoxusTokens.DarkSurface
                                            )
                                        ) {
                                            Text("ENVIAR REPORTE AL ÁRBITRO IA", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = { onCompleteChallengeWithoutEvidence(false) }
                                ) {
                                    Text(
                                        text = "Guardar sin evidencia",
                                        color = FoxusTokens.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    ChallengePhase.COMPLETED_VICTORY -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(FoxusTokens.DeepNavy)
                                .border(1.dp, FoxusTokens.FoxGold.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FoxCelebrationBurst()
                            Text(
                                text = "¡Reto superado! +$animatedEarnedXp XP",
                                style = MaterialTheme.typography.titleLarge,
                                color = FoxusTokens.FoxGold,
                                textAlign = TextAlign.Center
                            )

                            val verdict = uiState.latestRefereeVerdict
                            if (verdict != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(FoxusTokens.SurfaceCard)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = if (verdict.verified) {
                                            "✅ VEREDICTO ÁRBITRO IA (${verdict.confidenceScore}%)"
                                        } else {
                                            "🦊 REVISIÓN DEL ÁRBITRO IA"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = FoxusTokens.SuccessEmerald
                                    )
                                    Text(
                                        text = verdict.refereeSpeech,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FoxusTokens.WarmCream
                                    )
                                    Text(
                                        text = "• Detectado: ${verdict.detectedProgress}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FoxusTokens.TextSecondary
                                    )
                                    Text(
                                        text = "• Siguiente paso: ${verdict.nextTip}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FoxusTokens.FoxGold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onOpenProfile,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FoxusTokens.FoxOrange,
                                    contentColor = FoxusTokens.DarkSurface
                                )
                            ) {
                                Text("SEGUIR MI ENGANCHE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Collapsible Reasoning & Multi-Agent Trace
        TextButton(onClick = { showTechDetails = !showTechDetails }) {
            Text(
                text = if (showTechDetails) "Ocultar detalles de IA" else "Ver detalles",
                color = FoxusTokens.SlateBlue
            )
            Icon(
                imageVector = if (showTechDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = FoxusTokens.SlateBlue
            )
        }

        AnimatedVisibility(visible = showTechDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FoxusTokens.SurfaceCard)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Por qué funciona: ${mission.microtask.rationale}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxusTokens.WarmCream
                )
                Text(
                    text = "Modelo generativo: ${mission.microtask.modelName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoxusTokens.TextSecondary
                )
                TextButton(onClick = onOpenTraces) {
                    Text("Ver traza multiagente completa ➔", color = FoxusTokens.FoxGold)
                }
            }
        }
    }
}
