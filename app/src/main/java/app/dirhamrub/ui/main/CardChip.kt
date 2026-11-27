package app.dirhamrub.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun EmvChip(ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tint = ink.copy(alpha = 0.20f)
        val width = 1.dp.toPx().coerceAtMost(size.height * 0.11f)
        val inset = width / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, inset),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(4.dp.toPx().coerceAtMost(size.height / 4f)),
            style = Stroke(width),
        )
        val left = size.width * 0.26f
        val right = size.width * 0.74f
        val upper = size.height * 0.36f
        val lower = size.height * 0.64f
        drawLine(tint, Offset(left, upper), Offset(right, upper), width, StrokeCap.Round)
        drawLine(tint, Offset(left, lower), Offset(right, lower), width, StrokeCap.Round)
    }
}
