package app.dirhamrub.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import app.dirhamrub.ui.theme.LocalReduceMotion

/**
 * Число, у которого при смене «перекатываются» только изменившиеся символы: старый уходит вверх,
 * новый приходит снизу, с небольшой задержкой справа налево — как у счётчика.
 * Символы сопоставляются справа (копейки к копейкам), поэтому «359,20» → «3 749,15» меняет хвост на месте.
 * Цифры моноширинные (tnum), ширина строки не прыгает. При отключённых анимациях — обычный текст.
 * Для TalkBack — одна строка целиком, а не символы по отдельности.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val a11y = Modifier.clearAndSetSemantics { contentDescription = text }
    if (LocalReduceMotion.current) {
        Text(text, style = style, color = color, maxLines = 1, softWrap = false, modifier = modifier.then(a11y))
        return
    }
    Row(modifier.then(a11y)) {
        val n = text.length
        for (i in 0 until n) {
            val fromRight = n - 1 - i
            key(fromRight) {
                AnimatedContent(
                    targetState = text[i],
                    transitionSpec = {
                        val delay = minOf(fromRight, MAX_STAGGERED) * STAGGER_MS
                        (slideInVertically(tween(ROLL_MS, delay)) { it / 2 } + fadeIn(tween(ROLL_MS, delay))) togetherWith
                            (slideOutVertically(tween(ROLL_MS, delay)) { -it / 2 } + fadeOut(tween(ROLL_MS * 2 / 3, delay))) using
                            SizeTransform(clip = false)
                    },
                    label = "rolling",
                ) { ch ->
                    Text(ch.toString(), style = style, color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private const val ROLL_MS = 240
private const val STAGGER_MS = 22
private const val MAX_STAGGERED = 8
