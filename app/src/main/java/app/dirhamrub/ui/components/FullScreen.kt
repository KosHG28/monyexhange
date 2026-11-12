package app.dirhamrub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Что может экран [FullScreen]: закрыться с анимацией и потом выполнить [then] (сохранить, удалить). */
class FullScreenScope internal constructor(
    private val visible: MutableTransitionState<Boolean>,
    private val pending: MutableState<(() -> Unit)?>,
) {
    fun close(then: (() -> Unit)? = null) {
        pending.value = then
        visible.targetState = false
    }
}

/**
 * Экран поверх всего (вместо шторки): своё окно на весь экран, въезжает справа и уезжает вправо — как
 * настройки; закрывается «←» ([FullScreenScope.close]) и системным «Назад». Шторки тянулись пальцем и
 * застревали на полпути, у экрана тянуть нечего. [interceptBack] — перехватить «Назад» (несохранённые правки):
 * true — экран остаётся. Своё окно — чтобы экран ложился поверх чего угодно (главного, настроек, знакомства).
 */
@Composable
fun FullScreen(
    onDismissRequest: () -> Unit,
    interceptBack: () -> Boolean = { false },
    /** Фон на весь экран, под строкой состояния (флаг страны) — без отступов от краёв. */
    backdrop: @Composable BoxScope.() -> Unit = {},
    content: @Composable FullScreenScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val visible = remember { MutableTransitionState(reduceMotion).apply { targetState = true } }
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = remember { FullScreenScope(visible, pending) }
    // Уехал совсем — тогда сообщаем наверх (и выполняем отложенное действие).
    if (visible.isIdle && !visible.currentState && !visible.targetState) {
        LaunchedEffect(Unit) { (pending.value ?: onDismissRequest)() }
    }
    val colors = AppTheme.colors
    Dialog(
        onDismissRequest = { if (!interceptBack()) scope.close() },
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
        AnimatedVisibility(
            visibleState = visible,
            enter = if (reduceMotion) EnterTransition.None else slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = if (reduceMotion) ExitTransition.None else slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(180)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.bg),
            ) {
                backdrop()
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { scope.content() }
            }
        }
    }
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
