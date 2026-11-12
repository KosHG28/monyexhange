package app.dirhamrub.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.dirhamrub.ui.theme.AppTheme

/**
 * Шторка приложения: фон под ней затемнён (38 % в тёмной теме, 32 % в светлой), касание по фону закрывает.
 * Все шторки — только через неё, чтобы выглядели одинаково. Размытия нет: оно стоило кадров при открытии и закрытии.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.bg,
        scrimColor = Color.Black.copy(alpha = if (colors.isDark) 0.38f else 0.32f),
        content = content,
    )
}
