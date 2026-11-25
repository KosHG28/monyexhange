package app.dirhamrub.ui.theme

import android.graphics.Bitmap
import android.os.Build
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.wallet.CardMaterial
import kotlin.math.PI
import kotlin.math.abs
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
    material: CardMaterial?,
    tilt: State<Offset>,
    sweep: State<Float>?,
    radius: Dp,
): Modifier = key(style.ink) {
    val start by animateColorAsState(style.start, motion(240), label = "cardSurfaceStart")
    val end by animateColorAsState(style.end, motion(240), label = "cardSurfaceEnd")
    val dark = AppTheme.colors.isDark
    drawWithCache {
        val background = Brush.linearGradient(listOf(start, end))
        val texture = material?.let { cardMaterialLayer(it, radius) }
        val edgeWidth = 1.dp.toPx()
        val inset = edgeWidth / 2f
        val edgeSize = Size(size.width - edgeWidth, size.height - edgeWidth)
        val corner = CornerRadius(radius.toPx() - inset)
        val stroke = Stroke(edgeWidth)
        val clearLight = Color.White.copy(alpha = 0f)
        onDrawBehind {
            drawRect(background)
            texture?.invoke(this)

            if (material == CardMaterial.METAL) {
                val angle = tilt.value
                val strength = maxOf(abs(angle.x), abs(angle.y))
                val progress = sweep?.value ?: 1f
                val hotspot = center + Offset(size.width * angle.x * 0.36f, size.height * angle.y * 0.41f)
                val position = if (progress < 1f) {
                    Offset(size.width * (-0.19f + 1.38f * progress), hotspot.y)
                } else hotspot
                drawRect(metalHighlight(size.minDimension, position, 0.43f + 0.13f * strength))
                return@onDrawBehind
            }
            // Наличные имеют собственные динамические блики поверх бумажного рисунка.
            if (material != null) return@onDrawBehind
            drawCardGrain(alpha = 0.025f)
            val angle = tilt.value
            val strength = maxOf(abs(angle.x), abs(angle.y))
            val progress = sweep?.value ?: 1f
            val sweepAlpha = REFLECTION_PEAK * sin(progress.coerceIn(0f, 1f) * PI).toFloat()
            val spotAlpha = (0.15f + 0.07f * strength).coerceAtMost(REFLECTION_PEAK - sweepAlpha)
            val hotspot = center + Offset(size.width * angle.x * 0.36f, size.height * angle.y * 0.41f)
            val spread = size.width * 0.48f
            if (spotAlpha > 0.001f) withTransform({
                rotate(-32f, pivot = hotspot)
                scale(1f, 0.5f, pivot = hotspot)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = spotAlpha),
                        0.38f to Color.White.copy(alpha = spotAlpha * 0.72f),
                        1f to clearLight,
                        center = hotspot,
                        radius = spread,
                    ),
                    radius = spread,
                    center = hotspot,
                )
            }
            if (sweepAlpha > 0.001f) {
                val band = Offset(size.width * (-0.26f + 1.52f * progress), size.height * 0.5f)
                val feather = Offset(size.width * 0.19f, size.height * 0.19f)
                drawRect(
                    Brush.linearGradient(listOf(clearLight, Color.White.copy(alpha = sweepAlpha), clearLight), band - feather, band + feather),
                )
            }
            val phase = PI * (-0.5 + 0.64 * angle.x + 0.36 * angle.y)
            val direction = Offset(cos(phase).toFloat(), sin(phase).toFloat())
            val edgeScale = maxOf(abs(direction.x), abs(direction.y))
            val edgePosition = center + Offset(direction.x * edgeSize.width, direction.y * edgeSize.height) / (2f * edgeScale)
            if (dark) {
                val baseWidth = DarkSurfaceOutlineWidth.toPx()
                drawRoundRect(DARK_SURFACE_OUTLINE, Offset(baseWidth / 2f, baseWidth / 2f),
                    Size(size.width - baseWidth, size.height - baseWidth), CornerRadius(radius.toPx() - baseWidth / 2f),
                    style = Stroke(baseWidth))
            }
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.14f + 0.18f * strength), clearLight),
                    edgePosition, size.height * 0.66f,
                ),
                topLeft = Offset(inset, inset),
                size = edgeSize,
                cornerRadius = corner,
                style = stroke,
            )
        }
    }
}

private const val REFLECTION_PEAK = 0.22f
