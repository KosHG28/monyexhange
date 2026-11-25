package app.dirhamrub.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.wallet.CardMaterial

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

/** Без общего наклона миниатюра металла показывает зеркальную полосу в нейтральном положении. */
internal fun Modifier.cardMaterial(
    style: CardStyle,
    material: CardMaterial,
    radius: Dp,
    tilt: State<Float>? = null,
): Modifier = drawWithCache {
    val fill = Brush.linearGradient(listOf(style.start, style.end))
    val finish = cardMaterialLayer(material, radius)
    val rest = Offset(size.width * 0.52f, size.height * 0.5f)
    val metalLight = if (material == CardMaterial.METAL) metalHighlight(size.minDimension, rest, 0.43f) else null
    onDrawBehind {
        drawRect(fill)
        finish(this)
        if (metalLight != null) {
            val angle = tilt?.value ?: 0f
            val light = if (angle == 0f) metalLight else metalHighlight(
                size.minDimension, rest + Offset(size.width * angle * 0.36f, 0f), 0.43f,
            )
            drawRect(light)
        }
    }
}

internal fun metalHighlight(shortSide: Float, center: Offset, alpha: Float): Brush {
    val feather = Offset(shortSide * 0.18f, shortSide * 0.075f)
    return Brush.linearGradient(
        0f to Color.Transparent,
        0.23f to Color.Black.copy(alpha = alpha * 0.32f),
        0.42f to Color.White.copy(alpha = alpha * 0.15f),
        0.60f to Color.White.copy(alpha = alpha),
        0.69f to Color.White.copy(alpha = alpha * 0.74f),
        1f to Color.Transparent,
        start = center - feather,
        end = center + feather,
    )
}

internal fun CacheDrawScope.cardMaterialLayer(material: CardMaterial, radius: Dp): DrawScope.() -> Unit = when (material) {
    CardMaterial.SATIN -> { { drawCardGrain() } }
    CardMaterial.METAL -> { { drawRect(brushedMetal) } }
    CardMaterial.CARBON -> {
        val tileScale = 4.dp.toPx() / WEAVE_CELL_PIXELS
        val tileBounds = Size(size.width / tileScale, size.height / tileScale)
        val edgeWidth = 1.dp.toPx().coerceAtMost(size.minDimension)
        val inset = Offset(edgeWidth / 2f, edgeWidth / 2f)
        val edgeSize = Size(size.width - edgeWidth, size.height - edgeWidth)
        val rounding = CornerRadius((radius.toPx() - inset.x).coerceAtLeast(0f))
        val stroke = Stroke(edgeWidth)
        val draw: DrawScope.() -> Unit = {
            scale(tileScale, tileScale, pivot = Offset.Zero) { drawRect(carbonWeave, size = tileBounds) }
            drawRoundRect(Color(0xBF080C12), inset, edgeSize, rounding, style = stroke)
        }
        draw
    }
    CardMaterial.GLASS -> {
        val haze = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.025f)))
        val facet = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * 0.49f, 0f)
            lineTo(size.width * 0.23f, size.height)
            lineTo(0f, size.height)
            close()
        }
        val edgeWidth = 1.5.dp.toPx().coerceAtMost(size.minDimension)
        val inset = Offset(edgeWidth / 2f, edgeWidth / 2f)
        val edgeSize = Size(size.width - edgeWidth, size.height - edgeWidth)
        val rounding = CornerRadius((radius.toPx() - inset.x).coerceAtLeast(0f))
        val stroke = Stroke(edgeWidth)
        val bevel = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val draw: DrawScope.() -> Unit = {
            drawRect(haze)
            drawPath(facet, Color.White.copy(alpha = 0.16f))
            drawRoundRect(bevel, inset, edgeSize, rounding, style = stroke)
        }
        draw
    }
}
