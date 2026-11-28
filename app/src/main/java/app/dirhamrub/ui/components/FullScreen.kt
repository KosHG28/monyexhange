package app.dirhamrub.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.dirhamrub.ui.theme.Springs
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.HighFrameRate
import app.dirhamrub.ui.theme.AdaptiveFrameRate
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Что может экран [FullScreen]: закрыться с анимацией и потом выполнить [then] (сохранить, удалить). */
class FullScreenScope internal constructor(
    private val pending: MutableState<(() -> Unit)?>,
    private val closing: MutableState<Boolean>,
) {
    val isClosing: Boolean get() = closing.value

    fun close(then: (() -> Unit)? = null) {
        if (closing.value) return
        pending.value = then
        closing.value = true
    }
}

internal const val EDITOR_SURFACE_MOTION_MS = 260

/** Fullscreen content with safe insets, either inside the navigation stack or in its own window. */
@Composable
fun FullScreen(
    onDismissRequest: () -> Unit,
    interceptBack: () -> Boolean = { false },
    backdrop: @Composable BoxScope.() -> Unit = {},
    inline: Boolean = false,
    backEnabled: Boolean = true,
    editorMotion: Boolean = false,
    morph: CardMorph? = null,
    onExitStarted: () -> Unit = {},
    content: @Composable FullScreenScope.() -> Unit,
) {
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val closing = remember { mutableStateOf(false) }
    val scope = remember { FullScreenScope(pending, closing) }
    val reduceMotion = LocalReduceMotion.current
    val colors = AppTheme.colors
    val background = MaterialTheme.colorScheme.background
    val flight = morph?.takeIf { !reduceMotion && !inline }
    val screen: @Composable () -> Unit = {
        val backdropLayer = if (flight == null) Modifier else Modifier.graphicsLayer {
            alpha = if (flight.fallback) 1f else flight.backgroundAlpha
        }
        val contentLayer = if (flight == null) Modifier else Modifier.graphicsLayer {
            alpha = if (flight.fallback) 1f else flight.contentAlpha
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(backdropLayer).background(background), content = backdrop)
            Box(Modifier.fillMaxSize().then(contentLayer).windowInsetsPadding(WindowInsets.safeDrawing)) {
                scope.content()
            }
            if (flight != null) CardMorphOverlay(flight)
        }
    }
    val close = { if (!interceptBack()) scope.close() }
    if (inline) {
        BackHandler(enabled = backEnabled, onBack = close)
        LaunchedEffect(closing.value) {
            if (closing.value) (pending.value ?: onDismissRequest)()
        }
        screen()
        return
    }
    val visible = remember { MutableTransitionState(reduceMotion || flight != null).apply { targetState = true } }
    val cardFlight = LocalCardFlight.current
    val hasFlightTarget = flight?.target?.isEmpty == false
    LaunchedEffect(flight, hasFlightTarget, flight?.targetMeasured, closing.value) {
        if (closing.value) {
            onExitStarted()
            val returnOrigin = flight?.let { cardFlight?.originOf(it.cardId) }
            if (flight != null && hasFlightTarget && returnOrigin != null) {
                flight.returnOrigin = returnOrigin
                flight.closing = true
                flight.progress.animateTo(0f, tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing))
            } else if (flight != null) {
                flight.fallback = true
            }
            visible.targetState = false
        } else if (flight != null && !flight.settled) {
            if (hasFlightTarget) {
                flight.progress.animateTo(1f, tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing))
                flight.settled = true
            } else if (flight.targetMeasured) {
                flight.fallback = true
                flight.settled = true
            }
        }
    }
    if (visible.isIdle && !visible.currentState && !visible.targetState) {
        LaunchedEffect(Unit) { (pending.value ?: onDismissRequest)() }
    }
    val animatedScreen: @Composable () -> Unit = {
        HighFrameRate(!visible.isIdle || flight?.progress?.isRunning == true)
        AnimatedVisibility(
            visibleState = visible,
            enter = when {
                reduceMotion || flight != null -> EnterTransition.None
                editorMotion -> fadeIn(tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing)) { (it * 0.08f).toInt() } +
                    scaleIn(tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing), initialScale = 0.96f)
                else -> Springs.screenIn
            },
            exit = when {
                reduceMotion -> ExitTransition.None
                flight?.fallback == true -> fadeOut(tween(120))
                flight != null -> ExitTransition.None
                editorMotion -> fadeOut(tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing)) +
                    slideOutVertically(tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing)) { (it * 0.08f).toInt() } +
                    scaleOut(tween(EDITOR_SURFACE_MOTION_MS, easing = FastOutSlowInEasing), targetScale = 0.96f)
                else -> Springs.screenOut
            },
        ) { screen() }
    }
    if (flight != null) {
        // Both thumbnails and the editor preview are positioned in the same Compose root.
        BackHandler(enabled = backEnabled, onBack = close)
        animatedScreen()
        return
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setDimAmount(0f)
                it.setWindowAnimations(0)
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = !colors.isDark
                    isAppearanceLightNavigationBars = !colors.isDark
                }
            }
        }
        AdaptiveFrameRate {
            animatedScreen()
        }
    }
}

@Composable
internal fun ScrollBarDivider(scrolled: Boolean) {
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (scrolled) 0.4f else 0f)
    HorizontalDivider(thickness = 0.5.dp, color = color)
}

/** Верх экрана: «←», заголовок (может не быть) и действия справа (корзина). */
@Composable
fun ScreenBar(onBack: () -> Unit, title: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        if (title != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = AppTheme.type.title.copy(fontSize = 21.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}
