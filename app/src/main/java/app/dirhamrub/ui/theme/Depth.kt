package app.dirhamrub.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Глубина в тёмной теме: у верхнего края карточки — тонкая светлая полоса, как свет сверху. Слои читаются без
 * резких рамок. В светлой теме — ничего. Ставить после clip и фона карточки.
 */
fun Modifier.topLight(): Modifier = composed {
    if (!AppTheme.colors.isDark) return@composed this
    drawWithContent {
        drawContent()
        val h = 1.5.dp.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent), startY = 0f, endY = h * 2),
            size = Size(size.width, h * 2),
        )
    }
}
