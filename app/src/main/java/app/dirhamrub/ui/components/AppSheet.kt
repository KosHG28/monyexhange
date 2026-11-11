package app.dirhamrub.ui.components

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Сколько шторок открыто (или открывается): пока хоть одна — [SheetBlurHost] размывает экран под ними. */
private val LocalSheetDepth = staticCompositionLocalOf<MutableIntState?> { null }

/**
 * Шторка приложения: фон под ней затемнён (38 % в тёмной теме, 32 % в светлой) и размыт ([SheetBlurHost]),
 * касание по фону закрывает. Все шторки — только через неё, чтобы выглядели одинаково.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val depth = LocalSheetDepth.current
    // Размытие следует за тем же признаком, что и затемнение Material: куда шторка едет, а не есть ли она.
    // Иначе при закрытии фон оставался размытым, пока шторка уезжала, и проявлялся уже после неё — рывок.
    val shown = sheetState.targetValue != SheetValue.Hidden
    DisposableEffect(depth, shown) {
        if (shown) depth?.let { it.intValue += 1 }
        onDispose { if (shown) depth?.let { it.intValue -= 1 } }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.bg,
        scrimColor = Color.Black.copy(alpha = if (colors.isDark) 0.38f else 0.32f),
        content = content,
    )
}

/** Экран сейчас под шторкой: бесконечные анимации на нём лучше остановить — каждый их кадр заново размывает весь экран. */
@Composable
fun underSheet(): Boolean = (LocalSheetDepth.current?.intValue ?: 0) > 0

/**
 * Корень экрана: размывает всё под открытыми шторками. Шторка рисуется в своём окне, поэтому её саму
 * размытие не задевает. Размытие есть с Android 12; на старых остаётся одно затемнение.
 * Радиус меняется только в слое отрисовки — экран под шторкой не пересобирается на каждом кадре;
 * длительность и кривая те же, что у затемнения шторки (300 мс), чтобы они шли вместе.
 */
@Composable
fun SheetBlurHost(content: @Composable () -> Unit) {
    val depth = remember { mutableIntStateOf(0) }
    val reduceMotion = LocalReduceMotion.current
    val progress = animateFloatAsState(
        targetValue = if (depth.intValue > 0) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(SHEET_FADE_MS, easing = FastOutSlowInEasing),
        label = "sheetBlur",
    )
    CompositionLocalProvider(LocalSheetDepth provides depth) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val r = progress.value * BLUR_RADIUS.toPx()
                    renderEffect = if (r >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BlurEffect(r, r, TileMode.Clamp)
                    } else {
                        null
                    }
                    clip = renderEffect != null
                },
        ) { content() }
    }
}

private const val SHEET_FADE_MS = 300
private val BLUR_RADIUS = 10.dp
