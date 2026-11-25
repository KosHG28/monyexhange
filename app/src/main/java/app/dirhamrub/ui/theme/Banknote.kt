package app.dirhamrub.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Печатная рамка и защитный узор в стиле банкноты. */
internal fun Modifier.banknotePattern(ink: Color): Modifier = drawWithCache {
    val inset = minOf(5.dp.toPx(), size.height * 0.12f)
    val thin = Stroke(0.7.dp.toPx())
    val centerX = size.width * 0.7f
    val centerY = size.height * 0.5f
    val radius = minOf(size.width * 0.21f, size.height * 0.33f)
    val lines = Path()
    for (i in -2..2) {
        val y = centerY + i * radius * 0.13f
        lines.moveTo(centerX - radius, y)
        lines.cubicTo(centerX - radius * 0.4f, y - radius, centerX + radius * 0.4f, y + radius, centerX + radius, y)
        lines.cubicTo(centerX + radius * 0.4f, y - radius, centerX - radius * 0.4f, y + radius, centerX - radius, y)
    }
    onDrawBehind {
        drawRoundRect(ink.copy(alpha = 0.35f), Offset(inset, inset),
            Size(size.width - 2 * inset, size.height - 2 * inset), CornerRadius(inset * 0.6f), style = thin)
        drawPath(lines, ink.copy(alpha = 0.23f), style = thin)
    }
}
