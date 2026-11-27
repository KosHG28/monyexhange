package app.dirhamrub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun ContactlessIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 18.dp, height = 22.dp)) {
        val center = Offset(-size.width * 0.35f, size.height / 2f)
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        repeat(4) { index ->
            val radius = size.width * (0.55f + index * (1.25f - 0.55f) / 3f)
            drawArc(
                color = tint,
                startAngle = -26f,
                sweepAngle = 52f,
                useCenter = false,
                topLeft = center - Offset(radius, radius),
                size = Size(radius * 2f, radius * 2f),
                style = stroke,
            )
        }
    }
}
