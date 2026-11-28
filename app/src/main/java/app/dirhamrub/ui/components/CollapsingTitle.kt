package app.dirhamrub.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.Springs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Заголовок экрана, который сжимается при прокрутке (Material 3 Expressive): сверху крупный (30 sp), прокрутили —
 * он уменьшается, гаснет и «садится» в строку рядом с «←» (18 sp), под строкой появляется тонкая черта. Отпустили
 * на полпути — доезжает пружиной туда, где ближе. [progress]: 0 — раскрыт, 1 — в строке.
 */
class CollapsingTitle internal constructor(
    private val offset: () -> Float,
    private val range: () -> Float,
) {
    internal val distance: Float get() = range().coerceAtLeast(1f)
    val progress: Float get() = (offset() / distance).coerceIn(0f, 1f)
}

@Composable
fun rememberCollapsingTitle(scroll: ScrollState, titleHeight: Dp = LARGE_TITLE_HEIGHT): CollapsingTitle {
    val range = with(LocalDensity.current) { titleHeight.toPx() }
    val title = remember(scroll, range) { CollapsingTitle({ scroll.value.toFloat() }, { minOf(range, scroll.maxValue.toFloat()) }) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(scroll, title, reduceMotion) {
        settleTitleWhenIdle({ scroll.isScrollInProgress }) {
            val v = scroll.value
            val distance = title.distance
            if (v > 0 && v < distance.toInt()) {
                val target = if (v < distance / 2f) 0 else distance.toInt()
                if (reduceMotion) scroll.scrollTo(target) else scroll.animateScrollTo(target, Springs.spatial())
            }
        }
    }
    return title
}

@Composable
internal fun rememberCollapsingTitle(scroll: LazyListState): CollapsingTitle {
    val range = with(LocalDensity.current) { LARGE_TITLE_HEIGHT.roundToPx() }
    val title = remember(scroll, range) {
        CollapsingTitle({ if (scroll.firstVisibleItemIndex == 0) scroll.firstVisibleItemScrollOffset.toFloat() else range.toFloat() },
            { range.toFloat() })
    }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(scroll, title, reduceMotion) {
        settleTitleWhenIdle({ scroll.isScrollInProgress }) {
            val offset = scroll.firstVisibleItemScrollOffset
            if (scroll.firstVisibleItemIndex == 0 && offset in 1 until range) {
                val target = if (offset < range / 2) 0 else range
                if (target == 0 || scroll.canScrollForward) {
                    if (reduceMotion) scroll.scrollToItem(0, target) else scroll.animateScrollToItem(0, target)
                }
            }
        }
    }
    return title
}

internal suspend fun settleTitleWhenIdle(isScrolling: () -> Boolean, settle: suspend () -> Unit) = coroutineScope {
    snapshotFlow(isScrolling).collect { scrolling ->
        // A new gesture can cancel the scroll mutation without stopping the observer.
        if (!scrolling) launch { settle() }.join()
    }
}

/** Строка экрана: «←» и заголовок, который появляется, когда крупный заголовок уехал. */
@Composable
fun CollapsingBar(title: String, onBack: () -> Unit, state: CollapsingTitle, fillOnCollapse: Boolean = false) {
    val colors = AppTheme.colors
    val compactTitleVisible by remember(state) { derivedStateOf { state.progress >= 0.5f } }
    Column(if (fillOnCollapse) Modifier.drawBehind { drawRect(colors.bg, alpha = state.progress) } else Modifier.background(colors.bg)) {
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
                    .then(if (fillOnCollapse && compactTitleVisible) Modifier.semantics { heading() }
                        else Modifier.clearAndSetSemantics { })
                    .graphicsLayer {
                        val p = state.progress
                        alpha = p
                        translationY = (1f - p) * 8.dp.toPx()
                    },
            )
        }
        val scrolled by remember(state) { derivedStateOf { state.progress > 0f } }
        ScrollBarDivider(scrolled)
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
