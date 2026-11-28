package app.dirhamrub.ui.theme

import android.graphics.Bitmap
import android.os.Build
import android.view.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import app.dirhamrub.domain.wallet.CardMaterial
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private val cardTexture by lazy {
    val random = Random(24)
    val side = 96
    val pixels = IntArray(side * side) { if (random.nextBoolean()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    ShaderBrush(ImageShader(Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

internal fun DrawScope.drawCardGrain(alpha: Float = 0.02f) {
    drawRect(cardTexture, alpha = alpha,
        blendMode = if (Build.VERSION.SDK_INT >= 29) BlendMode.Softlight else BlendMode.Overlay)
}

@Composable
internal fun rememberReflectionTilt(active: Boolean): State<Offset> {
    val target = remember { mutableStateOf(Offset.Zero) }
    val filter = remember { CardTiltFilter() }
    val reduce = LocalReduceMotion.current
    ObserveRotation(
        active,
        onStart = { filter.reset(target.value) },
        onAcceleration = { values, time, rotation ->
            target.value = filter.readAcceleration(values, time, rotation)
        },
    ) { matrix, timestamp, rotation ->
        target.value = filter.read(matrix, timestamp, rotation)
    }
    SideEffect { if (reduce) target.value = Offset.Zero }
    val damping = if (reduce) snap() else spring(dampingRatio = 0.87f, stiffness = 460f, visibilityThreshold = 0.003f)
    val x = animateFloatAsState(if (reduce) 0f else target.value.x, damping, label = "cardTiltX")
    val y = animateFloatAsState(if (reduce) 0f else target.value.y, damping, label = "cardTiltY")
    return remember(x, y) { derivedStateOf { Offset(x.value, y.value) } }
}

/** The new resting orientation is recorded once on resume. Held angles keep their position. */
internal class CardTiltFilter {
    private val rest = FloatArray(6)
    private val horizontal = TiltPublicationGate()
    private val vertical = TiltPublicationGate()
    private var lastTime: Long? = null
    private var restGravity = Offset.Zero
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
        val x = (rest[0] * matrix[2] + rest[1] * matrix[5] + rest[2] * matrix[8]) / TILT_RANGE
        val y = -(rest[3] * matrix[2] + rest[4] * matrix[5] + rest[5] * matrix[8]) / TILT_RANGE
        return publish(x, y, timestamp, rotation)
    }

    fun readAcceleration(gravity: FloatArray, timestamp: Long, rotation: Int): Offset {
        val (x, y, z) = gravity
        val norm = sqrt(x * x + y * y + z * z)
        if (norm !in 5f..15f) return published
        val angles = Offset(-atan2(x, sqrt(y * y + z * z)), -atan2(y, z))
        if (lastTime == null) restGravity = angles
        val pitch = angles.y - restGravity.y
        val wrappedPitch = atan2(sin(pitch), cos(pitch))
        return publish((angles.x - restGravity.x) / TILT_RANGE, wrappedPitch / TILT_RANGE, timestamp, rotation)
    }

    private fun publish(x: Float, y: Float, timestamp: Long, rotation: Int): Offset {
        val time = lastTime
        lastTime = timestamp
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
    material: CardMaterial,
    tilt: State<Offset>,
    sweep: State<Float>?,
    radius: Dp,
): Modifier = key(style.ink) {
    val start by animateColorAsState(style.start, motion(240), label = "cardSurfaceStart")
    val end by animateColorAsState(style.end, motion(240), label = "cardSurfaceEnd")
    drawWithCache {
        val background = Brush.linearGradient(listOf(start, end))
        val finish = cardMaterialLayer(material, radius, tilt, sweep, lightCard = style.isLightCard())
        onDrawBehind {
            drawRect(background)
            finish(this)
        }
    }
}

private const val TILT_RANGE = 0.38f
