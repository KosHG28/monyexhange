package app.dirhamrub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion

internal val ScreenActionHeight = 52.dp
internal val ScreenActionRadius = 16.dp

@Composable
internal fun ScreenAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    confirmed: Boolean = false,
) {
    val colors = AppTheme.colors
    val accent = enabled || loading
    val foreground = when {
        confirmed -> colors.good
        accent -> colors.onAccent
        else -> colors.muted
    }
    Row(
        modifier = modifier.heightIn(min = ScreenActionHeight).clip(RoundedCornerShape(ScreenActionRadius))
            .background(when {
                confirmed -> colors.goodSoft
                accent -> colors.accent
                else -> colors.surface2
            })
            .clickable(enabled = enabled && !loading && !confirmed, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                if (confirmed || loading) liveRegion = LiveRegionMode.Polite
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            val indicator = Modifier.size(16.dp).clearAndSetSemantics { }
            if (LocalReduceMotion.current) {
                CircularProgressIndicator(progress = { 0.7f }, modifier = indicator, color = foreground, strokeWidth = 2.dp)
            } else {
                CircularProgressIndicator(modifier = indicator, color = foreground, strokeWidth = 2.dp)
            }
        }
        Text(label, modifier = Modifier.clearAndSetSemantics { },
            style = AppTheme.type.bodyStrong.copy(fontSize = 17.sp),
            color = foreground, maxLines = 1)
    }
}
