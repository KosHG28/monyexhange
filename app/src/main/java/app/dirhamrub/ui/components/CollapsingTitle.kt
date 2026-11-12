package app.dirhamrub.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.Springs

/**
 * Заголовок экрана, который сжимается при прокрутке (Material 3 Expressive): сверху крупный (30 sp), прокрутили —
 * он уменьшается, гаснет и «садится» в строку рядом с «←» (18 sp), под строкой появляется тонкая черта. Отпустили
 * на полпути — доезжает пружиной туда, где ближе. [progress]: 0 — раскрыт, 1 — в строке.
 */
class CollapsingTitle internal constructor(private val scroll: ScrollState, internal val rangePx: Float) {
    val progress: Float get() = (scroll.value / rangePx).coerceIn(0f, 1f)
}

@Composable
fun rememberCollapsingTitle(scroll: ScrollState): CollapsingTitle {
    val range = with(LocalDensity.current) { LARGE_TITLE_HEIGHT.toPx() }
    val title = remember(scroll, range) { CollapsingTitle(scroll, range) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(scroll.isScrollInProgress) {
        if (scroll.isScrollInProgress) return@LaunchedEffect
        val v = scroll.value
        if (v > 0 && v < range) {
            val target = if (v < range / 2f) 0 else range.toInt()
            if (reduceMotion) scroll.scrollTo(target) else scroll.animateScrollTo(target, Springs.spatial())
        }
    }
    return title
}

/** Строка экрана: «←» и заголовок, который появляется, когда крупный заголовок уехал. */
@Composable
fun CollapsingBar(title: String, onBack: () -> Unit, state: CollapsingTitle) {
    val colors = AppTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.settings_back)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = back, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back, tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = AppTheme.type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    // Для TalkBack заголовок — крупный, этот только виден.
                    .clearAndSetSemantics { }
                    .graphicsLayer {
                        val p = state.progress
                        alpha = p
                        translationY = (1f - p) * 8.dp.toPx()
                    },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .graphicsLayer { alpha = state.progress }
                .background(colors.line),
        )
    }
}

/** Крупный заголовок — первым в прокручиваемом содержимом. */
@Composable
fun LargeTitle(title: String, state: CollapsingTitle, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(LARGE_TITLE_HEIGHT), contentAlignment = Alignment.BottomStart) {
        Text(
            title,
            style = AppTheme.type.title.copy(fontSize = 30.sp, lineHeight = 36.sp),
            color = AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 4.dp, bottom = 6.dp)
                .semantics { heading() }
                .graphicsLayer {
                    val p = state.progress
                    alpha = 1f - p
                    val s = 1f - 0.4f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 1f)
                },
        )
    }
}

private val LARGE_TITLE_HEIGHT = 56.dp
