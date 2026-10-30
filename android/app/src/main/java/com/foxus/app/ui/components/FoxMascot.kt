package com.foxus.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foxus.app.ui.theme.FoxusTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Official 7 emotional & operational states of FOX (Section 2).
 */
enum class FoxMascotState {
    IDLE,
    CURIOUS,
    CHALLENGE,
    FOCUS,
    VICTORY,
    SLEEP,
    PLAYFUL
}

/**
 * Faceted geometric low-poly mascot derived directly from `assets/brand/foxus_logo.png`.
 * Articulates ears, head tilt, breathing, angular eyes, wink, focus satellite, shockwave rings,
 * rising Zzz glyphs, and low-poly celebratory confetti natively in Jetpack Compose.
 */
@Composable
fun FoxMascot(
    state: FoxMascotState,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp
) {
    val infinite = rememberInfiniteTransition(label = "fox_infinite")

    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    FoxMascotState.CHALLENGE, FoxMascotState.VICTORY -> 1000
                    FoxMascotState.FOCUS, FoxMascotState.SLEEP -> 3200
                    else -> 2100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fox_breath"
    )

    val blinkScaleY by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3400
                1f at 0
                1f at 3050
                0.08f at 3170
                1f at 3310
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "fox_blink"
    )

    val playfulWinkScaleY by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                1f at 0
                1f at 1650
                0.10f at 1820
                0.10f at 1980
                1f at 2150
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "fox_wink"
    )

    val cycleProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fox_cycle"
    )

    val focusOrbitAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fox_focus_orbit"
    )

    val playfulSwing by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fox_playful"
    )

    val targetTilt = when (state) {
        FoxMascotState.CURIOUS -> 9f
        FoxMascotState.PLAYFUL -> 7f * playfulSwing
        FoxMascotState.SLEEP -> -5f
        else -> 0f
    }
    val headTilt by animateFloatAsState(
        targetValue = targetTilt,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 260f),
        label = "fox_tilt"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Mascota FOXUS en estado ${state.name}" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w * 0.5f, h * 0.5f)

        // 1. State-specific ambient aura behind FOX
        val auraColor = when (state) {
            FoxMascotState.CHALLENGE -> FoxusTokens.FoxOrange.copy(alpha = 0.28f + 0.14f * breathPhase)
            FoxMascotState.VICTORY -> FoxusTokens.FoxGold.copy(alpha = 0.36f + 0.16f * breathPhase)
            FoxMascotState.PLAYFUL -> FoxusTokens.FoxGold.copy(alpha = 0.24f + 0.08f * breathPhase)
            FoxMascotState.FOCUS -> FoxusTokens.SlateBlue.copy(alpha = 0.20f + 0.08f * breathPhase)
            FoxMascotState.CURIOUS -> FoxusTokens.FoxGold.copy(alpha = 0.20f + 0.08f * breathPhase)
            FoxMascotState.SLEEP -> FoxusTokens.DeepNavy.copy(alpha = 0.32f)
            FoxMascotState.IDLE -> FoxusTokens.DeepNavy.copy(alpha = 0.48f)
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(auraColor, Color.Transparent),
                center = center,
                radius = w * 0.49f
            ),
            radius = w * 0.49f,
            center = center
        )

        // 2. State-specific background geometric effects
        when (state) {
            FoxMascotState.CHALLENGE -> drawChallengeShockwaves(center, w, cycleProgress)
            FoxMascotState.FOCUS -> drawFocusOrbitSatellite(center, w, focusOrbitAngle)
            FoxMascotState.VICTORY -> drawLowPolyVictoryConfetti(center, w, cycleProgress)
            else -> Unit
        }

        // 3. Articulated geometric fox head mapped on a 108x108 virtual grid
        val verticalBob = when (state) {
            FoxMascotState.VICTORY -> -h * 0.055f * breathPhase
            FoxMascotState.CHALLENGE -> -h * 0.028f * breathPhase
            FoxMascotState.PLAYFUL -> -h * 0.020f * breathPhase
            else -> -h * 0.014f * breathPhase
        }
        val breathScale = when (state) {
            FoxMascotState.VICTORY -> 1f + 0.042f * breathPhase
            FoxMascotState.CHALLENGE -> 1f + 0.032f * breathPhase
            else -> 1f + 0.022f * breathPhase
        }

        translate(left = 0f, top = verticalBob) {
            rotate(degrees = headTilt, pivot = center) {
                scale(scaleX = breathScale, scaleY = breathScale, pivot = center) {
                    drawGeometricFoxHead(
                        state = state,
                        blinkScaleY = blinkScaleY,
                        playfulWinkScaleY = playfulWinkScaleY,
                        earPerk = breathPhase,
                        playfulSwing = playfulSwing
                    )
                }
            }
        }

        // 4. Foreground cues: Curious floating diamond or Sleep rising Zzz
        if (state == FoxMascotState.CURIOUS) {
            drawCuriousSparkDiamond(w, h, breathPhase)
        } else if (state == FoxMascotState.SLEEP) {
            drawSleepZzzGlyphs(w, h, cycleProgress)
        }
    }
}

/**
 * Full-width low-poly geometric confetti banner used on challenge completion.
 */
@Composable
fun FoxCelebrationBurst(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "victory_burst")
    val fallProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_fall"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        val count = 16
        for (i in 0 until count) {
            val xRatio = (i + 0.5f) / count
            val localPhase = (fallProgress + (i * 0.17f)) % 1f
            val cx = size.width * xRatio + sin((localPhase * 2 * PI + i).toDouble()).toFloat() * 10.dp.toPx()
            val cy = size.height * localPhase
            val rot = localPhase * 360f * if (i % 2 == 0) 1f else -1f
            val shardSize = 6.dp.toPx()
            val shardColor = when (i % 3) {
                0 -> FoxusTokens.FoxGold
                1 -> FoxusTokens.FoxOrange
                else -> FoxusTokens.WarmCream
            }.copy(alpha = (1f - 0.55f * localPhase).coerceIn(0.2f, 1f))

            rotate(degrees = rot, pivot = Offset(cx, cy)) {
                val diamond = Path().apply {
                    moveTo(cx, cy - shardSize)
                    lineTo(cx + shardSize * 0.75f, cy)
                    lineTo(cx, cy + shardSize)
                    lineTo(cx - shardSize * 0.75f, cy)
                    close()
                }
                drawPath(diamond, color = shardColor)
            }
        }
    }
}

private fun DrawScope.pt(x108: Float, y108: Float): Offset =
    Offset(x108 / 108f * size.width, y108 / 108f * size.height)

private fun DrawScope.poly(vararg coords: Pair<Float, Float>): Path = Path().apply {
    if (coords.isNotEmpty()) {
        val first = pt(coords[0].first, coords[0].second)
        moveTo(first.x, first.y)
        for (i in 1 until coords.size) {
            val next = pt(coords[i].first, coords[i].second)
            lineTo(next.x, next.y)
        }
        close()
    }
}

private fun DrawScope.drawGeometricFoxHead(
    state: FoxMascotState,
    blinkScaleY: Float,
    playfulWinkScaleY: Float,
    earPerk: Float,
    playfulSwing: Float
) {
    val leftEarLift = when (state) {
        FoxMascotState.CURIOUS -> -3.8f
        FoxMascotState.CHALLENGE, FoxMascotState.VICTORY -> -2.8f * earPerk
        FoxMascotState.SLEEP -> 3.8f
        FoxMascotState.PLAYFUL -> -3.0f * playfulSwing
        else -> -1.0f * earPerk
    }
    val rightEarLift = when (state) {
        FoxMascotState.CURIOUS -> 1.2f
        FoxMascotState.CHALLENGE, FoxMascotState.VICTORY -> -2.8f * earPerk
        FoxMascotState.SLEEP -> 3.8f
        FoxMascotState.PLAYFUL -> 3.0f * playfulSwing
        else -> -1.0f * earPerk
    }

    // Outer & inner ears (preserving official angular proportions)
    drawPath(
        poly(25f to (18f + leftEarLift), 28f to 44f, 38f to 33f),
        color = FoxusTokens.FoxBurntOrange
    )
    drawPath(
        poly(25f to (18f + leftEarLift), 44f to 32f, 38f to 33f),
        color = Color(0xFFE96D1B)
    )
    drawPath(
        poly(83f to (18f + rightEarLift), 70f to 33f, 80f to 44f),
        color = FoxusTokens.FoxBurntOrange
    )
    drawPath(
        poly(83f to (18f + rightEarLift), 70f to 33f, 64f to 32f),
        color = Color(0xFFE96D1B)
    )

    // Upper orange cheeks
    drawPath(
        poly(28f to 44f, 44f to 32f, 50f to 63f, 22f to 57f),
        color = FoxusTokens.FoxOrange
    )
    drawPath(
        poly(80f to 44f, 86f to 57f, 58f to 63f, 64f to 32f),
        color = FoxusTokens.FoxOrange
    )

    // Golden crown & nose bridge
    drawPath(
        poly(44f to 32f, 64f to 32f, 58f to 52f, 57.5f to 74f, 50.5f to 74f, 50f to 52f),
        color = FoxusTokens.FoxGold
    )

    // Warm tan jaw shadow
    drawPath(
        poly(30f to 68f, 49f to 85f, 59f to 85f, 78f to 68f, 70f to 78f, 58f to 88f, 50f to 88f, 38f to 78f),
        color = FoxusTokens.WarmTan
    )

    // Warm cream muzzle & lower cheeks
    drawPath(
        poly(
            22f to 57f,
            50f to 63f,
            50.5f to 74f,
            54f to 81f,
            57.5f to 74f,
            58f to 63f,
            86f to 57f,
            77f to 69f,
            59f to 84f,
            49f to 84f,
            31f to 69f
        ),
        color = FoxusTokens.WarmCream
    )

    // Expressive eyes by state
    val eyeColor = Color(0xFF1B2830)
    when (state) {
        FoxMascotState.SLEEP -> {
            drawLine(
                color = eyeColor,
                start = pt(36f, 55f),
                end = pt(46f, 57f),
                strokeWidth = size.width * 0.026f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = eyeColor,
                start = pt(72f, 55f),
                end = pt(62f, 57f),
                strokeWidth = size.width * 0.026f,
                cap = StrokeCap.Round
            )
        }
        FoxMascotState.VICTORY -> {
            val stroke = Stroke(width = size.width * 0.028f, cap = StrokeCap.Round)
            val leftHappy = Path().apply {
                val p1 = pt(36f, 57f)
                val p2 = pt(41.5f, 50.5f)
                val p3 = pt(47f, 57f)
                moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y)
            }
            val rightHappy = Path().apply {
                val p1 = pt(61f, 57f)
                val p2 = pt(66.5f, 50.5f)
                val p3 = pt(72f, 57f)
                moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y)
            }
            drawPath(leftHappy, color = eyeColor, style = stroke)
            drawPath(rightHappy, color = eyeColor, style = stroke)
        }
        else -> {
            val leftEyePivot = pt(42f, 55f)
            scale(scaleX = 1f, scaleY = blinkScaleY, pivot = leftEyePivot) {
                drawPath(
                    poly(36f to 50f, 47f to 55f, 45.5f to 59.5f, 37.5f to 57.5f),
                    color = eyeColor
                )
            }
            val rightEyeScaleY = if (state == FoxMascotState.PLAYFUL) {
                minOf(blinkScaleY, playfulWinkScaleY)
            } else {
                blinkScaleY
            }
            val rightEyePivot = pt(66f, 55f)
            scale(scaleX = 1f, scaleY = rightEyeScaleY, pivot = rightEyePivot) {
                drawPath(
                    poly(72f to 50f, 70.5f to 57.5f, 62.5f to 59.5f, 61f to 55f),
                    color = eyeColor
                )
            }
        }
    }

    // Charcoal nose tip
    drawPath(
        poly(49.5f to 74f, 58.5f to 74f, 54f to 80.5f),
        color = eyeColor
    )
}

private fun DrawScope.drawChallengeShockwaves(center: Offset, width: Float, progress: Float) {
    for (ring in 0..1) {
        val p = (progress + ring * 0.5f) % 1f
        val r = width * (0.28f + 0.20f * p)
        val alpha = (0.55f * (1f - p)).coerceIn(0f, 0.55f)
        val diamondRing = Path().apply {
            moveTo(center.x, center.y - r)
            lineTo(center.x + r, center.y)
            lineTo(center.x, center.y + r)
            lineTo(center.x - r, center.y)
            close()
        }
        drawPath(
            path = diamondRing,
            color = FoxusTokens.FoxOrange.copy(alpha = alpha),
            style = Stroke(width = width * 0.015f)
        )
    }
}

private fun DrawScope.drawFocusOrbitSatellite(center: Offset, width: Float, orbitDeg: Float) {
    val orbitRadius = width * 0.43f
    drawCircle(
        color = FoxusTokens.SlateBlue.copy(alpha = 0.25f),
        radius = orbitRadius,
        center = center,
        style = Stroke(width = width * 0.010f)
    )
    val rad = orbitDeg * PI / 180.0
    val satPos = Offset(
        x = center.x + (cos(rad) * orbitRadius).toFloat(),
        y = center.y + (sin(rad) * orbitRadius).toFloat()
    )
    drawCircle(
        color = FoxusTokens.FoxGold.copy(alpha = 0.35f),
        radius = width * 0.042f,
        center = satPos
    )
    drawCircle(
        color = FoxusTokens.FoxGold,
        radius = width * 0.022f,
        center = satPos
    )
}

private fun DrawScope.drawLowPolyVictoryConfetti(center: Offset, width: Float, progress: Float) {
    val count = 12
    for (i in 0 until count) {
        val angle = (2.0 * PI * i / count) + (progress * 1.1)
        val dist = width * (0.24f + 0.24f * progress)
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val pos = Offset(
            x = center.x + (cos(angle) * dist).toFloat(),
            y = center.y + (sin(angle) * dist).toFloat()
        )
        val shard = width * (0.032f * (1.15f - 0.45f * progress))
        val color = when (i % 3) {
            0 -> FoxusTokens.FoxGold.copy(alpha = alpha)
            1 -> FoxusTokens.FoxOrange.copy(alpha = alpha)
            else -> FoxusTokens.WarmCream.copy(alpha = alpha)
        }
        rotate(degrees = progress * 240f + i * 30f, pivot = pos) {
            val diamond = Path().apply {
                moveTo(pos.x, pos.y - shard)
                lineTo(pos.x + shard * 0.7f, pos.y)
                lineTo(pos.x, pos.y + shard)
                lineTo(pos.x - shard * 0.7f, pos.y)
                close()
            }
            drawPath(diamond, color = color)
        }
    }
}

private fun DrawScope.drawCuriousSparkDiamond(width: Float, height: Float, breathPhase: Float) {
    val cx = width * 0.20f
    val cy = height * (0.16f - 0.03f * breathPhase)
    val r = width * (0.038f + 0.012f * breathPhase)
    val spark = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.75f, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r * 0.75f, cy)
        close()
    }
    drawPath(spark, color = FoxusTokens.FoxGold)
}

private fun DrawScope.drawSleepZzzGlyphs(width: Float, height: Float, progress: Float) {
    val strokeW = width * 0.016f
    for (idx in 0..2) {
        val phase = (progress + idx * 0.33f) % 1f
        val alpha = (1f - phase).coerceIn(0f, 0.88f)
        val zx = width * (0.72f + 0.11f * phase)
        val zy = height * (0.30f - 0.20f * phase)
        val s = width * (0.028f + 0.014f * phase)
        val zPath = Path().apply {
            moveTo(zx - s, zy - s)
            lineTo(zx + s, zy - s)
            lineTo(zx - s, zy + s)
            lineTo(zx + s, zy + s)
        }
        drawPath(
            path = zPath,
            color = FoxusTokens.WarmCream.copy(alpha = alpha),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}
