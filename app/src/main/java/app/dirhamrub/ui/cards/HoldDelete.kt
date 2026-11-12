package app.dirhamrub.ui.cards

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.Springs
import app.dirhamrub.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Корзина «удалить удержанием» (Material 3 Expressive): держите 1 с — вокруг заполняется волнистое кольцо, корзина
 * краснеет, в конце мягкий «бум» и [onDelete]. Отпустили раньше — кольцо пружиной убегает назад, ничего не удалено;
 * короткое касание — [onTapHint] («держите, чтобы удалить»). TalkBack — [onAccessibleDelete] (окно подтверждения).
 */
@Composable
internal fun HoldDeleteButton(description: String, onDelete: () -> Unit, onTapHint: () -> Unit, onAccessibleDelete: () -> Unit) {
    val colors = AppTheme.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val start = SystemClock.uptimeMillis()
                        haptics.play(Haptic.TICK)
                        val fill = scope.launch {
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            haptics.play(Haptic.THUD)
                            onDelete()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, Springs.exit()) }
                            if (SystemClock.uptimeMillis() - start < TAP_MS) onTapHint()
                        }
                    },
                )
            }
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onAccessibleDelete()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(42.dp)) {
            val p = progress.value
            if (p <= 0f) return@Canvas
            val stroke = 2.5.dp.toPx()
            val r = size.minDimension / 2f - 3.dp.toPx()
            val amp = 1.6.dp.toPx()
            val path = Path()
            val steps = (WAVE_POINTS * p).toInt().coerceAtLeast(2)
            for (i in 0..steps) {
                val t = i.toFloat() / WAVE_POINTS
                val a = -PI / 2 + 2 * PI * t
                val rr = r + amp * sin(WAVES * 2 * PI * t).toFloat()
                val x = center.x + rr * cos(a).toFloat()
                val y = center.y + rr * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors.danger, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Icon(
            painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = lerp(colors.muted, colors.danger, progress.value),
            modifier = Modifier.size(24.dp),
        )
    }
}

private const val HOLD_MS = 1000
private const val TAP_MS = 250L
private const val WAVE_POINTS = 96
private const val WAVES = 10
