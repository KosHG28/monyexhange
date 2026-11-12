package app.dirhamrub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * Волнистая полоса загрузки (Material 3 Expressive) — пока читается скриншот или качаются курсы: по тонкой
 * дорожке бежит отрезок-волна. Без анимаций в системе — ровная полоса. Для TalkBack — «идёт загрузка».
 */
@Composable
fun WavyProgress(modifier: Modifier = Modifier, color: Color = AppTheme.colors.accent) {
    val track = AppTheme.colors.line
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "wavy")
    val head = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "head")
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val stroke = 3.dp.toPx()
        val mid = size.height / 2f
        drawLine(track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        if (reduceMotion) {
            drawLine(color, Offset(0f, mid), Offset(size.width * 0.4f, mid), strokeWidth = stroke, cap = StrokeCap.Round)
            return@Canvas
        }
        // Отрезок длиной 40 % ширины выезжает слева и уезжает вправо; по нему бежит волна.
        val len = size.width * 0.4f
        val start = -len + (size.width + len) * head.value
        val from = start.coerceAtLeast(0f)
        val to = (start + len).coerceAtMost(size.width)
        if (to <= from) return@Canvas
        val amp = 2.5.dp.toPx()
        val wave = 18.dp.toPx()
        val path = Path()
        var x = from
        var first = true
        while (x <= to) {
            val y = mid + amp * sin(2 * PI * (x / wave - phase.value)).toFloat()
            if (first) path.moveTo(x, y) else path.lineTo(x, y)
            first = false
            x += 2f
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
