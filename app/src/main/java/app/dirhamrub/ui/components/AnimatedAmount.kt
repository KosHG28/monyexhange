package app.dirhamrub.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.key
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Анимируем только показ готового результата: сам расчёт и округление всегда точные. */
@Composable
fun AnimatedAmount(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) {
        Text(text, style = style, color = color, maxLines = 1, softWrap = false, modifier = modifier)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, character ->
            // Ключ от правого края: при переходе 999 → 1 000 сохраняются разряды справа.
            key(text.length - index) {
                AnimatedContent(character, contentAlignment = Alignment.CenterEnd, label = "amountDigit",
                    transitionSpec = {
                        (fadeIn(tween(150)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
                            (fadeOut(tween(90)) + slideOutVertically(tween(160)) { -it / 4 }) using SizeTransform(clip = false)
                    }) { value -> Text(value.toString(), style = style, color = color, maxLines = 1, softWrap = false) }
            }
        }
    }
}
