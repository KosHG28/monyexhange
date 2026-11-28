package app.dirhamrub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import app.dirhamrub.ui.theme.HighFrameRate
import app.dirhamrub.ui.theme.LocalReduceMotion

private const val PAGE_DURATION = 270

@Composable
internal fun SlidePage(visible: Boolean, content: @Composable () -> Unit) {
    val snap = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = if (snap) EnterTransition.None else slideInHorizontally(tween(PAGE_DURATION, easing = FastOutSlowInEasing)) { it },
        exit = if (snap) ExitTransition.None else slideOutHorizontally(tween(PAGE_DURATION, easing = FastOutSlowInEasing)) { it },
    ) {
        HighFrameRate(transition.isRunning)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** Preserve scroll and input state on the screen behind the current destination. */
@Composable
internal fun PageUnderlay(
    pageOnTop: Boolean,
    editorOnTop: Boolean = false,
    editorOpen: Boolean = editorOnTop,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val slide by animateFloatAsState(
        if (pageOnTop && !reduceMotion) 1f else 0f,
        if (reduceMotion) snap() else tween(PAGE_DURATION, easing = FastOutSlowInEasing),
        label = "pageUnderlay",
    )
    val depth by animateFloatAsState(
        if (editorOnTop && !reduceMotion) 1f else 0f,
        if (reduceMotion) snap() else tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing),
        label = "editorUnderlay",
    )
    val returning by remember { derivedStateOf { slide > 0f || depth > 0f } }
    val hideInput = pageOnTop || editorOpen || returning
    val accessibility = if (hideInput) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier
    Box(Modifier.fillMaxSize().then(accessibility)
        .graphicsLayer {
            translationX = size.width * -0.25f * slide
            scaleX = 1f - 0.03f * depth
            scaleY = scaleX
        }
        .drawWithContent {
            drawContent()
            val dim = maxOf(0.12f * slide, 0.18f * depth)
            if (dim > 0f) drawRect(Color.Black, alpha = dim)
        },
    ) {
        content()
        if (hideInput) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
        }
    }
}
