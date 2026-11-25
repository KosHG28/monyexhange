package app.dirhamrub.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.wallet.CardMaterial

/** Штрихи и плетение повторяются из двух маленьких текстур во всех превью и миниатюрах. */
private val brushedMetal by lazy {
    materialTile(width = 48, height = 32) { x, y ->
        when {
            y % 4 != 0 -> 0
            (x + y * 17) % 48 < 27 -> 0x0EFFFFFF
            else -> 0x08000000
        }
    }
}

private val carbonWeave by lazy {
    materialTile(width = 24, height = 24) { x, y ->
        val inverting = (x / 12 + y / 12) % 2 != 0
        val thread = (if (inverting) x + y else x - y) and 3
        when (thread) {
            0 -> 0x0FFFFFFF
            2 -> 0x0B000000
            else -> 0
        }
    }
}

private fun materialTile(width: Int, height: Int, pixel: (Int, Int) -> Int): ShaderBrush {
    val colors = IntArray(width * height) { index -> pixel(index % width, index / width) }
    val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** Миниатюры используют статический блик без подписки на датчик. */
internal fun Modifier.cardMaterial(material: CardMaterial, radius: Dp, grain: Boolean = true): Modifier = drawWithCache {
    val texture = cardMaterialLayer(material, radius, grain)
    val metalLight = if (material == CardMaterial.METAL) {
        metalHighlight(size.width, Offset(size.width * 0.52f, size.height * 0.48f), 0.16f)
    } else null
    onDrawBehind {
        texture(this)
        if (metalLight != null) drawRect(metalLight)
    }
}

internal fun metalHighlight(width: Float, center: Offset, alpha: Float): Brush {
    val feather = Offset(width * 0.29f, width * 0.15f)
    return Brush.linearGradient(
        0f to Color.Transparent,
        0.40f to Color.White.copy(alpha = alpha * 0.52f),
        0.53f to Color.White.copy(alpha = alpha),
        0.68f to Color.White.copy(alpha = alpha * 0.28f),
        1f to Color.Transparent,
        start = center - feather,
        end = center + feather,
    )
}

internal fun CacheDrawScope.cardMaterialLayer(
    material: CardMaterial,
    radius: Dp,
    grain: Boolean = true,
): DrawScope.() -> Unit {
    val frame = 1.dp.toPx()
    val stroke = Stroke(frame)
    val inset = Offset(frame / 2f, frame / 2f)
    val frameSize = Size((size.width - frame).coerceAtLeast(0f), (size.height - frame).coerceAtLeast(0f))
    val rounding = CornerRadius((radius.toPx() - frame / 2f).coerceAtLeast(0f))
    return when (material) {
        CardMaterial.SATIN -> { { if (grain) drawCardGrain() } }
        CardMaterial.METAL -> { { drawRect(brushedMetal) } }
        CardMaterial.CARBON -> {
            val bevel = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.30f)))
            val draw: DrawScope.() -> Unit = {
                drawRect(carbonWeave)
                drawRoundRect(bevel, inset, frameSize, rounding, style = stroke)
            }
            draw
        }
        CardMaterial.GLASS -> {
            val frost = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.24f),
                0.13f to Color.White.copy(alpha = 0.12f),
                0.48f to Color.White.copy(alpha = 0.025f),
                1f to Color.Black.copy(alpha = 0.06f),
            )
            val draw: DrawScope.() -> Unit = {
                drawRect(frost)
                drawRoundRect(Color.White.copy(alpha = 0.24f), inset, frameSize, rounding, style = stroke)
            }
            draw
        }
    }
}
