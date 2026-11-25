package app.dirhamrub.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.wallet.CardMaterial

/** Материал на градиенте карты. Миниатюры используют статический блик без подписки на датчик. */
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

/** Paths and glass/edge gradients are cached for the current card size. */
internal fun CacheDrawScope.cardMaterialLayer(
    material: CardMaterial,
    radius: Dp,
    grain: Boolean = true,
): DrawScope.() -> Unit = when (material) {
    CardMaterial.SATIN -> { { if (grain) drawCardGrain() } }
    CardMaterial.METAL -> {
        val strokes = Path()
        val step = 1.8.dp.toPx()
        val count = (size.height / step).toInt()
        repeat(count) { row ->
            val x = size.width * ((row * 31 % 89) / 130f)
            val y = (row + 0.5f) * step
            strokes.moveTo(x, y)
            strokes.lineTo((x + size.width * (0.29f + row % 7 * 0.08f)).coerceAtMost(size.width), y)
        }
        val fine = Stroke(0.65.dp.toPx())
        val shade = Color.White.copy(alpha = 0.055f)
        val draw: DrawScope.() -> Unit = { drawPath(strokes, shade, style = fine) }
        draw
    }
    CardMaterial.CARBON -> {
        val fibers = Path()
        val cell = 5.5.dp.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val x = column * cell
                val y = row * cell
                val reverse = (row + column) % 2 != 0
                for (thread in 1..3) {
                    val shift = thread * cell / 4f
                    if (reverse) {
                        fibers.moveTo(x + shift, y)
                        fibers.lineTo(x + cell, y + cell - shift)
                    } else {
                        fibers.moveTo(x, y + shift)
                        fibers.lineTo(x + cell - shift, y)
                    }
                }
            }
        }
        val fine = Stroke(0.65.dp.toPx())
        val frame = 1.dp.toPx()
        val bevel = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.30f)))
        val fiberColor = Color.White.copy(alpha = 0.06f)
        val inset = Offset(frame / 2f, frame / 2f)
        val frameSize = Size((size.width - frame).coerceAtLeast(0f), (size.height - frame).coerceAtLeast(0f))
        val rounding = CornerRadius((radius.toPx() - frame / 2f).coerceAtLeast(0f))
        val draw: DrawScope.() -> Unit = {
            drawPath(fibers, fiberColor, style = fine)
            drawRoundRect(bevel, inset, frameSize, rounding, style = Stroke(frame))
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
        val edge = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.48f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.24f)),
        )
        val frame = 1.dp.toPx()
        val inset = Offset(frame / 2f, frame / 2f)
        val frameSize = Size((size.width - frame).coerceAtLeast(0f), (size.height - frame).coerceAtLeast(0f))
        val rounding = CornerRadius((radius.toPx() - frame / 2f).coerceAtLeast(0f))
        val draw: DrawScope.() -> Unit = {
            drawRect(frost)
            drawRoundRect(edge, inset, frameSize, rounding, style = Stroke(frame))
        }
        draw
    }
}
