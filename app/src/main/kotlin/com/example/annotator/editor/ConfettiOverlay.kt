package com.example.annotator.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private const val PARTICLE_COUNT = 50
private val CONFETTI_COLORS = listOf(
    Color(0xFFFFD600), Color(0xFFFF4081), Color(0xFF40C4FF), Color(0xFF69F0AE), Color(0xFFFF6E40),
)

private class ConfettiParticle(
    val startX: Float,
    val fallDelay: Float,
    val horizontalDrift: Float,
    val spinSpeed: Float,
    val size: Float,
    val color: Color,
)

/**
 * One-shot celebratory confetti burst + message, auto-dismissing after [durationMs] via
 * [onFinished]. Deliberately cheap regardless of the current image's annotation count: a single
 * [Animatable] drives one progress value, and every particle's position/alpha/rotation for that
 * frame is derived inline inside the Canvas draw phase rather than each particle holding its own
 * animated state -- so this can't become the kind of per-annotation cost that
 * [ImageCanvas]'s mask rendering had to be moved off the main thread to avoid.
 */
@Composable
fun ConfettiOverlay(message: String, onFinished: () -> Unit, durationMs: Int = 2200) {
    val particles = remember {
        List(PARTICLE_COUNT) {
            ConfettiParticle(
                startX = Random.nextFloat(),
                fallDelay = Random.nextFloat() * 0.3f,
                horizontalDrift = (Random.nextFloat() - 0.5f) * 0.3f,
                spinSpeed = (Random.nextFloat() - 0.5f) * 20f,
                size = 6f + Random.nextFloat() * 6f,
                color = CONFETTI_COLORS[Random.nextInt(CONFETTI_COLORS.size)],
            )
        }
    }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMs, easing = LinearEasing))
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = progress.value
            particles.forEach { p ->
                val local = ((t - p.fallDelay) / (1f - p.fallDelay)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val x = (p.startX + p.horizontalDrift * local) * size.width
                val y = local * size.height * 1.1f
                val alpha = (1f - local * local).coerceIn(0f, 1f)
                rotate(degrees = p.spinSpeed * local * 36f, pivot = Offset(x, y)) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size / 2, y - p.size / 2),
                        size = Size(p.size, p.size),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.Center),
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                message,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
