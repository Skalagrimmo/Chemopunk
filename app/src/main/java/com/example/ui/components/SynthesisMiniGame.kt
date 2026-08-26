package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ImmersiveAccentOrange
import com.example.ui.theme.ImmersiveBackground
import com.example.ui.theme.ImmersiveSurface
import com.example.ui.theme.ImmersiveSurfaceVariant
import com.example.ui.theme.ImmersiveText
import com.example.ui.theme.ImmersiveTextMuted
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.ToxicRed

enum class SynthesisQuality(val label: String, val multiplier: Float, val color: Color) {
    PERFECT("PERFECT", 1.5f, PhosphorGreen),
    GOOD("GOOD", 1.0f, ImmersiveText),
    WASTE("WASTE", 0.5f, ToxicRed)
}

@Composable
fun SynthesisMiniGame(
    scienceSkill: Int,
    onBrew: (SynthesisQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SynthesisQuality?>(null) }

    val perfectWidth = 0.15f + scienceSkill * 0.01f
    val goodWidth = 0.30f + scienceSkill * 0.005f

    val baseSpeed = 1800 - scienceSkill * 80
    val speed = baseSpeed.coerceIn(600, 1800)

    val infiniteTransition = rememberInfiniteTransition(label = "slider")
    val animatedProgress by if (isRunning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = speed, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sliderAnim"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Position the cursor over the target zone and tap BREW.",
            color = ImmersiveTextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, ImmersiveSurfaceVariant, RoundedCornerShape(6.dp))
                .background(ImmersiveSurface, RoundedCornerShape(6.dp))
                .padding(4.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawSynthTrack(
                    progress = animatedProgress,
                    perfectWidth = perfectWidth,
                    goodWidth = goodWidth
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("WASTE", color = ToxicRed.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            Text("GOOD", color = ImmersiveTextMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            Text("PERFECT", color = PhosphorGreen.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            Text("GOOD", color = ImmersiveTextMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            Text("WASTE", color = ToxicRed.copy(alpha = 0.6f), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (result != null) {
            Text(
                "Quality: ${result!!.label} (${(result!!.multiplier * 100).toInt()}% potency)",
                color = result!!.color,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Button(
            onClick = {
                if (isRunning) {
                    isRunning = false
                    result = evaluateQuality(animatedProgress, perfectWidth, goodWidth)
                    onBrew(result!!)
                } else {
                    isRunning = true
                    result = null
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) ToxicRed else ImmersiveAccentOrange
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isRunning) "⚗ BREW NOW" else "▶ START MIXING",
                color = ImmersiveBackground,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun DrawScope.drawSynthTrack(
    progress: Float,
    perfectWidth: Float,
    goodWidth: Float
) {
    val w = size.width
    val h = size.height
    val centerX = w * 0.5f

    val perfectHalf = w * perfectWidth * 0.5f
    val goodHalf = w * goodWidth * 0.5f

    drawRoundRect(
        color = Color(0xFF1A1A2E),
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = CornerRadius(4f, 4f)
    )

    drawRect(
        color = ToxicRed.copy(alpha = 0.2f),
        topLeft = Offset(0f, 0f),
        size = Size(centerX - goodHalf, h)
    )
    drawRect(
        color = ToxicRed.copy(alpha = 0.2f),
        topLeft = Offset(centerX + goodHalf, 0f),
        size = Size(w - (centerX + goodHalf), h)
    )

    drawRect(
        color = ImmersiveText.copy(alpha = 0.15f),
        topLeft = Offset(centerX - goodHalf, 0f),
        size = Size(goodHalf - perfectHalf, h)
    )
    drawRect(
        color = ImmersiveText.copy(alpha = 0.15f),
        topLeft = Offset(centerX + perfectHalf, 0f),
        size = Size(goodHalf - perfectHalf, h)
    )

    drawRect(
        color = PhosphorGreen.copy(alpha = 0.25f),
        topLeft = Offset(centerX - perfectHalf, 0f),
        size = Size(perfectHalf * 2, h)
    )

    val cursorX = w * progress.coerceIn(0f, 1f)
    drawRect(
        color = ImmersiveAccentOrange,
        topLeft = Offset(cursorX - 2f, 0f),
        size = Size(4f, h)
    )
    drawCircle(
        color = ImmersiveAccentOrange,
        radius = 6f,
        center = Offset(cursorX, -2f)
    )
}

private fun evaluateQuality(progress: Float, perfectWidth: Float, goodWidth: Float): SynthesisQuality {
    val distFromCenter = kotlin.math.abs(progress - 0.5f)
    val perfectHalf = perfectWidth * 0.5f
    val goodHalf = goodWidth * 0.5f
    return when {
        distFromCenter <= perfectHalf -> SynthesisQuality.PERFECT
        distFromCenter <= goodHalf -> SynthesisQuality.GOOD
        else -> SynthesisQuality.WASTE
    }
}
