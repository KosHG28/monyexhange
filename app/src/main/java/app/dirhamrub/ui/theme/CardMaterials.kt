package app.dirhamrub.ui.theme

import android.graphics.Bitmap
import android.graphics.ComposeShader
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.wallet.CardMaterial
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun CardStyle.forMaterial(material: CardMaterial): CardStyle = when (material) {
    CardMaterial.CARBON -> copy(
        start = lerp(Color(0xFF242B35), start, 0.28f),
        end = lerp(Color(0xFF111820), end, 0.28f),
        ink = Color.White,
    )
    CardMaterial.GLASS -> copy(
        start = lerp(start, Color(0xFFE3F1F8), 0.58f).copy(alpha = 0.90f),
        end = lerp(end, Color(0xFFBED2E1), 0.64f).copy(alpha = 0.94f),
        ink = Color(0xFF111D28),
        mark = Color(0xFF223C50),
    )
    CardMaterial.SATIN, CardMaterial.METAL -> this
}

/** Шлифовка: короткие штрихи разной длины, без случайного мерцания при перерисовке. */
private val brushedMetal by lazy {
    materialTile(width = 64, height = 28) { x, y ->
        when {
            y % 4 == 0 && (x + y * 19) % 64 < 46 -> 0x19FFFFFF
            y % 4 == 1 && (x + y * 13) % 64 < 54 -> 0x16000000
            else -> 0
        }
    }
}

private const val WEAVE_CELL_PIXELS = 8

private val carbonWeave by lazy {
    val tile = WEAVE_CELL_PIXELS * 2
    materialTile(width = tile, height = tile) { x, y ->
        val crosswise = (x / WEAVE_CELL_PIXELS + y / WEAVE_CELL_PIXELS) % 2 != 0
        val facet = (if (crosswise) x + y else x - y) and 7
        when (facet) {
            in 0..2 -> 0x27FFFFFF
            in 4..6 -> 0x25000000
            else -> 0
        }
    }
}

private fun materialTile(width: Int, height: Int, pixel: (Int, Int) -> Int): ShaderBrush {
    val colors = IntArray(width * height) { index -> pixel(index % width, index / width) }
    val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** Движение задаётся для окна, образцы без датчика освещены под углом 35°. */
internal fun Modifier.cardMaterial(
    style: CardStyle,
    material: CardMaterial,
    radius: Dp,
    tilt: State<Offset>? = null,
): Modifier = drawWithCache {
    val fill = Brush.linearGradient(listOf(style.start, style.end))
    val finish = cardMaterialLayer(material, radius, tilt)
    onDrawBehind {
        drawRect(fill)
        finish(this)
    }
}

private val lightAngle = Math.toRadians(35.0)
// Нормаль поперёк блика, сама полоса лежит под углом 35°.
private val lightNormal = Offset(sin(lightAngle).toFloat(), cos(lightAngle).toFloat())

internal fun CacheDrawScope.cardMaterialLayer(
    material: CardMaterial,
    radius: Dp,
    tilt: State<Offset>?,
    sweep: State<Float>? = null,
): DrawScope.() -> Unit {
    if (size.minDimension <= 0f) return {}
    val bounds = size
    val depth = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.087f),
        0.53f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.14f),
        end = Offset(bounds.width, bounds.height),
    )
    val bevel = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.26f), Color.Black.copy(alpha = 0.36f)),
        end = Offset(bounds.width, bounds.height),
    )
    val outside = edge(radius, if (material == CardMaterial.GLASS) 0.75.dp else 0.85.dp)
    val inner = when (material) {
        CardMaterial.GLASS -> edge(radius, 1.5.dp, inset = 0.75.dp)
        CardMaterial.CARBON -> edge(radius, 1.1.dp, inset = 0.85.dp)
        else -> edge(radius, 1.1.dp)
    }

    val finish = when (material) {
        CardMaterial.SATIN -> {
            val draw: DrawScope.() -> Unit = {
                val angle = tilt?.value ?: Offset.Zero
                val strength = maxOf(abs(angle.x), abs(angle.y)).coerceAtMost(1f)
                val hotspot = Offset(bounds.width * (0.37f + angle.x * 0.25f), bounds.height * (0.31f + angle.y * 0.24f))
                drawCardGrain()
                drawRect(depth)
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.10f + 0.04f * strength), Color.Transparent),
                        center = hotspot, radius = bounds.width * 0.71f,
                    ),
                    blendMode = BlendMode.Screen,
                )
                inner.draw(this, bevel)
            }
            draw
        }
        CardMaterial.METAL -> {
            val draw: DrawScope.() -> Unit = {
                val angle = tilt?.value ?: Offset.Zero
                val progress = sweep?.value ?: 1f
                val position = if (progress < 1f) {
                    Offset(bounds.width * (-0.22f + 1.44f * progress), bounds.height * (0.49f + angle.y * 0.30f))
                } else {
                    Offset(bounds.width * (0.50f + angle.x * 0.47f), bounds.height * (0.49f + angle.y * 0.30f))
                }
                val feather = lightNormal * (bounds.minDimension * 0.17f)
                drawRect(brushedMetal)
                drawRect(depth)
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.38f to Color.White.copy(alpha = 0.07f),
                        0.51f to Color.White.copy(alpha = 0.38f),
                        0.61f to Color.White.copy(alpha = 0.23f),
                        1f to Color.Transparent,
                        start = position - feather, end = position + feather,
                    ),
                )
                inner.draw(this, bevel)
                val horizontal = (position.x / bounds.width - 0.5f) * 2.8f
                val rim = rimPoint(bounds, Offset(horizontal, -1f + angle.y * 0.53f))
                inner.draw(this, Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.67f), Color.Transparent),
                    center = rim, radius = bounds.minDimension * 0.55f,
                ))
            }
            draw
        }
        CardMaterial.CARBON -> {
            val tileScale = 4.dp.toPx() / WEAVE_CELL_PIXELS
            val tileBounds = Size(bounds.width / tileScale, bounds.height / tileScale)
            val tileShader = carbonWeave.createShader(tileBounds)
            val draw: DrawScope.() -> Unit = {
                val angle = tilt?.value ?: Offset.Zero
                val hotspot = Offset(bounds.width * (0.30f + angle.x * 0.40f), bounds.height * (0.34f + angle.y * 0.38f))
                val lacquerRadius = bounds.minDimension * 0.76f
                val mask = RadialGradient(
                    hotspot.x / tileScale, hotspot.y / tileScale, lacquerRadius / tileScale,
                    intArrayOf(0xDDFFFFFF.toInt(), 0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
                )
                val facets = ShaderBrush(ComposeShader(tileShader, mask, PorterDuff.Mode.DST_IN))
                scale(tileScale, tileScale, pivot = Offset.Zero) {
                    drawRect(carbonWeave, size = tileBounds)
                    drawRect(facets, size = tileBounds)
                }
                drawRect(depth)
                drawRect(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.21f),
                        0.42f to Color.White.copy(alpha = 0.085f),
                        1f to Color.Transparent, center = hotspot, radius = lacquerRadius,
                    ),
                )
                outside.draw(this, SolidColor(Color(0xBF080C12)))
                inner.draw(this, bevel)
            }
            draw
        }
        CardMaterial.GLASS -> {
            val haze = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.025f)))
            val draw: DrawScope.() -> Unit = {
                val angle = tilt?.value ?: Offset.Zero
                val position = Offset(bounds.width * (0.23f + angle.x * 0.40f), bounds.height * (0.42f + angle.y * 0.30f))
                val feather = lightNormal * (bounds.minDimension * 0.36f)
                val band = Color.White.copy(alpha = 0.25f)
                drawRect(haze)
                drawRect(depth)
                drawRect(Brush.linearGradient(
                    0f to Color.Transparent, 0.025f to band, 0.972f to band, 1f to Color.Transparent,
                    start = position - feather, end = position + feather,
                ))
                outside.draw(this, SolidColor(Color.Black.copy(alpha = 0.10f)))
                val rim = rimPoint(bounds, Offset(-0.68f + angle.x * 1.87f, -0.84f + angle.y * 2f))
                inner.draw(this, Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.12f)),
                    center = rim, radius = bounds.width * 0.77f,
                ))
            }
            draw
        }
    }
    return finish
}

private fun rimPoint(bounds: Size, direction: Offset): Offset {
    val limit = maxOf(abs(direction.x), abs(direction.y), 0.001f)
    return bounds.center + Offset(bounds.width * direction.x, bounds.height * direction.y) / (2f * limit)
}

private fun CacheDrawScope.edge(radius: Dp, width: Dp, inset: Dp = 0.dp): CardEdge {
    val space = inset.toPx().coerceIn(0f, size.minDimension * 0.25f)
    val stroke = width.toPx().coerceAtMost(size.minDimension - space * 2f)
    val offset = space + stroke / 2f
    return CardEdge(
        Offset(offset, offset), Size(size.width - offset * 2f, size.height - offset * 2f),
        CornerRadius((radius.toPx() - offset).coerceAtLeast(0f)), Stroke(stroke),
    )
}

private class CardEdge(val inset: Offset, val bounds: Size, val radius: CornerRadius, val stroke: Stroke) {
    fun draw(scope: DrawScope, brush: Brush) = with(scope) {
        drawRoundRect(brush, inset, bounds, radius, style = stroke)
    }
}
