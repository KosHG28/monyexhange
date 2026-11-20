package app.dirhamrub.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.domain.AppIcon

/** Same 108 dp layers cropped to the launcher's central 72 dp, rather than a tiny raw foreground. */
@Composable
internal fun LauncherPreview(icon: AppIcon, size: Dp = 44.dp) {
    val background = when (icon) {
        AppIcon.CLASSIC -> R.drawable.ic_launcher_background_classic
        AppIcon.GOLD, AppIcon.DARK -> R.drawable.ic_launcher_background_gold
        AppIcon.EMERALD -> R.drawable.ic_launcher_background_emerald
        AppIcon.PLATINUM -> R.drawable.ic_launcher_background_platinum
    }
    val foreground = when (icon) {
        AppIcon.CLASSIC -> R.drawable.ic_launcher_foreground
        AppIcon.GOLD, AppIcon.DARK -> R.drawable.ic_launcher_foreground_gold
        AppIcon.EMERALD -> R.drawable.ic_launcher_foreground_emerald
        AppIcon.PLATINUM -> R.drawable.ic_launcher_foreground_platinum
    }
    val shape = RoundedCornerShape(11.dp)
    Box(Modifier.size(size).clip(shape)) {
        for (resource in listOf(background, foreground)) {
            Image(painterResource(resource), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
        }
        if (icon == AppIcon.PLATINUM) Box(Modifier.fillMaxSize().border(1.dp, Color(0xFF0F172A).copy(alpha = 0.09f), shape))
    }
}
