package app.dirhamrub.ui.theme

import android.view.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

@Composable
internal fun rememberReflectionTilt(active: Boolean): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val filter = remember { CardTiltFilter() }
    val reduce = LocalReduceMotion.current
    ObserveRotation(active && !reduce, onStart = { filter.reset(tilt.value) }) { matrix, timestamp, rotation ->
        tilt.value = filter.read(matrix, timestamp, rotation)
    }
    SideEffect { if (reduce) tilt.value = Offset.Zero }
    return tilt
}

/** The new resting orientation is recorded once on resume. Held angles keep their position. */
internal class CardTiltFilter {
    private val rest = FloatArray(6)
    private val horizontal = TiltPublicationGate()
    private val vertical = TiltPublicationGate()
    private var lastTime: Long? = null
    private var smooth = Offset.Zero
    private var published = Offset.Zero

    fun reset(current: Offset) {
        lastTime = null
        smooth = current
        published = current
        horizontal.reset()
        vertical.reset()
        horizontal.accept(current.x)
        vertical.accept(current.y)
    }

    fun read(matrix: FloatArray, timestamp: Long, rotation: Int = Surface.ROTATION_0): Offset {
        val time = lastTime
        if (time == null) {
            // Baseline X and Y in world coordinates, used to project the current screen normal.
            for (axis in 0..2) {
                rest[axis] = matrix[axis * 3]
                rest[axis + 3] = matrix[axis * 3 + 1]
            }
        }
        lastTime = timestamp
        val x = (rest[0] * matrix[2] + rest[1] * matrix[5] + rest[2] * matrix[8]) / 0.38f
        val y = -(rest[3] * matrix[2] + rest[4] * matrix[5] + rest[5] * matrix[8]) / 0.38f
        val target = when (rotation) {
            Surface.ROTATION_90 -> Offset(-y, x)
            Surface.ROTATION_180 -> Offset(-x, -y)
            Surface.ROTATION_270 -> Offset(y, -x)
            else -> Offset(x, y)
        }
        val seconds = if (time == null) 0f else ((timestamp - time) / 1e9f).coerceIn(0f, 0.2f)
        val fraction = 1f - exp(-seconds / 0.060f)
        val nextX = smooth.x + (target.x.coerceIn(-1f, 1f) - smooth.x) * fraction
        val nextY = smooth.y + (target.y.coerceIn(-1f, 1f) - smooth.y) * fraction
        smooth = Offset(if (abs(nextX) < 0.001f) 0f else nextX, if (abs(nextY) < 0.001f) 0f else nextY)
        published = Offset(
            if (horizontal.accept(smooth.x)) smooth.x else published.x,
            if (vertical.accept(smooth.y)) smooth.y else published.y,
        )
        return published
    }
}

/** Reflection beneath the lettering, including a directional highlight inside the rounded edge. */
@Composable
internal fun Modifier.cardReflection(
    style: CardStyle,
    tilt: State<Offset>,
    sweep: State<Float>?,
    radius: Dp,
): Modifier = key(style.ink) {
    val start by animateColorAsState(style.start, motion(240), label = "cardSurfaceStart")
    val end by animateColorAsState(style.end, motion(240), label = "cardSurfaceEnd")
    drawWithCache {
        val background = Brush.linearGradient(listOf(start, end))
        val edgeWidth = 1.25.dp.toPx()
        val inset = edgeWidth / 2f
        val edgeSize = Size(size.width - edgeWidth, size.height - edgeWidth)
        val corner = CornerRadius(radius.toPx() - inset)
        val stroke = Stroke(edgeWidth)
        onDrawBehind {
            val angle = tilt.value
            val strength = maxOf(abs(angle.x), abs(angle.y))
            val progress = sweep?.value ?: 1f
            val sweepAlpha = if (progress in 0f..1f) 0.14f * sin(progress * PI).toFloat() else 0f
            val spotAlpha = (0.065f + 0.145f * strength).coerceAtMost(0.21f - sweepAlpha)
            val hotspot = center + Offset(size.width * angle.x * 0.39f, size.height * angle.y * 0.43f)
            drawRect(background)
            drawRect(
                Brush.radialGradient(listOf(Color.White.copy(alpha = spotAlpha), Color.Transparent), hotspot, size.width * 0.37f),
                blendMode = BlendMode.Softlight,
            )
            if (sweepAlpha > 0.001f) {
                val band = Offset(size.width * (-0.26f + 1.52f * progress), size.height * 0.5f)
                val feather = Offset(size.width * 0.19f, size.height * 0.19f)
                drawRect(
                    Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = sweepAlpha), Color.Transparent), band - feather, band + feather),
                    blendMode = BlendMode.Softlight,
                )
            }
            val edgePosition = center + Offset(
                size.width * 0.56f * angle.x,
                size.height * 0.54f * (angle.y * 0.62f + angle.x * 0.78f).coerceIn(-1f, 1f),
            )
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.08f + 0.13f * strength), Color.White.copy(alpha = 0.035f)),
                    edgePosition, size.width * 0.62f,
                ),
                topLeft = Offset(inset, inset),
                size = edgeSize,
                cornerRadius = corner,
                style = stroke,
            )
        }
    }
}
