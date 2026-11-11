package app.dirhamrub.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Сколько шторок открыто: пока хоть одна — [SheetBlurHost] размывает экран под ними. */
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
    DisposableEffect(depth) {
        depth?.let { it.intValue += 1 }
        onDispose { depth?.let { it.intValue -= 1 } }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.bg,
        scrimColor = Color.Black.copy(alpha = if (colors.isDark) 0.38f else 0.32f),
        content = content,
    )
}

/**
 * Корень экрана: размывает всё под открытыми шторками. Шторка рисуется в своём окне, поэтому её саму
 * размытие не задевает. Размытие есть с Android 12; на старых остаётся одно затемнение.
 */
@Composable
fun SheetBlurHost(content: @Composable () -> Unit) {
    val depth = remember { mutableIntStateOf(0) }
    val reduceMotion = LocalReduceMotion.current
    val radius by animateDpAsState(
        targetValue = if (depth.intValue > 0) 10.dp else 0.dp,
        animationSpec = if (reduceMotion) snap() else tween(220),
        label = "sheetBlur",
    )
    CompositionLocalProvider(LocalSheetDepth provides depth) {
        Box(Modifier.fillMaxSize().then(if (radius > 0.dp) Modifier.blur(radius) else Modifier)) { content() }
    }
}
