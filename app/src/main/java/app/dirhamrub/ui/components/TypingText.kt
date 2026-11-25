package app.dirhamrub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.HighFrameRate
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Цифры и разделители всегда рисуются вместе. Переход начинается уже с нового значения. */
@Composable
fun TypingText(text: String, style: TextStyle, color: Color, fractionColor: Color, empty: Boolean, modifier: Modifier = Modifier) {
    val fraction = SpanStyle(color = fractionColor, fontSize = style.fontSize * 0.82f)
    val formatted = remember(text, fraction) {
        buildAnnotatedString {
            append(text)
            val comma = text.indexOf(',')
            if (comma >= 0) addStyle(fraction, comma, length)
        }
    }
    val offset = remember { Animatable(0f) }
    var previous by remember { mutableStateOf(text) }
    val reduce = LocalReduceMotion.current
    HighFrameRate(!reduce && offset.isRunning)
    LaunchedEffect(text, empty, reduce) {
        val changed = text != previous
        previous = text
        offset.snapTo(0f)
        if (!reduce && changed) {
            offset.snapTo(if (empty) 2f else 1.25f)
            offset.animateTo(0f, tween(if (empty) 100 else 140, easing = FastOutSlowInEasing))
        }
    }
    Text(formatted, style = style, color = color, maxLines = 1, softWrap = false,
        modifier = modifier.graphicsLayer { translationY = offset.value.dp.toPx() })
}
