package app.dirhamrub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.Springs
import kotlinx.coroutines.delay

/**
 * Плашка «Карта удалена · Вернуть» внизу экрана (Material 3 Expressive): тёмная «таблетка» выпрыгивает пружиной,
 * через [durationMs] уходит сама. [visible] — показывать; [onUndo] — вернуть; [onTimeout] — время вышло.
 */
@Composable
fun UndoBar(
    visible: Boolean,
    text: String,
    action: String,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
    durationMs: Long = 5000,
) {
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(visible) {
        if (visible) {
            delay(durationMs)
            onTimeout()
        }
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reduceMotion) EnterTransition.None else slideInVertically(Springs.fastSpatial()) { it * 2 } + fadeIn(Springs.effects()),
            exit = if (reduceMotion) ExitTransition.None else slideOutVertically(Springs.exit()) { it * 2 } + fadeOut(Springs.fastEffects()),
        ) {
            val colors = AppTheme.colors
            // «Наоборот» к теме: тёмная плашка на светлом и светлая на тёмном — сразу видна поверх всего.
            val back = colors.ink
            val front = colors.bg
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(back)
                    .padding(start = 20.dp, end = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, style = AppTheme.type.body, color = front, modifier = Modifier.weight(1f))
                Text(
                    action,
                    style = AppTheme.type.bodyStrong,
                    color = if (colors.isDark) colors.accent else colors.accentSoft,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onUndo)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
        }
    }
}
