package app.dirhamrub.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

@Composable
internal fun EmvChip(metal: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val rim = 0.65.dp.toPx()
        val corner = CornerRadius(4.dp.toPx().coerceAtMost(size.height / 4f))
        val topLeft = Offset(rim / 2f, rim / 2f)
        val plate = Size(size.width - rim, size.height - rim)
        val warm = lerp(metal, Color(0xFF36302C), 0.46f)
        val shine = lerp(metal, Color.White, 0.70f)
        drawRoundRect(
            Brush.linearGradient(0f to shine, 0.43f to metal, 0.57f to lerp(metal, shine, 0.35f), 1f to warm),
            topLeft, plate, corner,
        )
        drawRoundRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.84f), warm.copy(alpha = 0.80f))),
            topLeft, plate, corner, style = Stroke(rim),
        )
        val contacts = Path()
        val left = size.width * 0.32f
        val right = size.width * 0.68f
        val top = size.height * 0.33f
        val bottom = size.height * 0.67f
        for (x in listOf(left, right)) {
            contacts.moveTo(x, rim)
            contacts.lineTo(x, size.height - rim)
        }
        for (y in listOf(top, bottom)) {
            contacts.moveTo(rim, y)
            contacts.lineTo(left, y)
            contacts.moveTo(right, y)
            contacts.lineTo(size.width - rim, y)
        }
        contacts.moveTo(left, size.height / 2f)
        contacts.lineTo(right, size.height / 2f)
        drawPath(contacts, warm.copy(alpha = 0.87f), style = Stroke(rim))
    }
}
