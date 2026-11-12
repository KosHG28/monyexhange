package app.dirhamrub.ui.cards

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pro: сохранённые скриншоты банка при чеках карты — маленькие превью в ряд и подпись; касание — скриншот на
 * весь экран.
 */
@Composable
internal fun ReceiptShotRow(shots: List<File>) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf<File?>(null) }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (f in shots.take(MAX_THUMBS)) {
            val thumb by rememberShot(f, THUMB_PX)
            Box(
                Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface2)
                    .clickable(role = Role.Image, onClickLabel = stringResource(R.string.shot_a11y)) { open = f },
            ) {
                thumb?.let { Image(it, contentDescription = stringResource(R.string.shot_a11y), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.width(2.dp))
        Text(stringResource(R.string.shot_saved), style = AppTheme.type.caption, color = colors.muted, modifier = Modifier.weight(1f))
    }
    open?.let { f -> ShotViewer(f) { open = null } }
}

/** Скриншот на весь экран, на чёрном; «×» или «Назад» — закрыть. */
@Composable
private fun ShotViewer(file: File, onClose: () -> Unit) {
    val image by rememberShot(file, FULL_PX)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            image?.let {
                Image(it, contentDescription = stringResource(R.string.shot_a11y), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            val close = stringResource(R.string.shot_close)
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = close,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(12.dp),
            )
        }
    }
}

/** Картинка из файла, уменьшенная до [maxPx] по длинной стороне, — читается в фоне. */
@Composable
private fun rememberShot(file: File, maxPx: Int) = produceState<ImageBitmap?>(null, file, file.lastModified()) {
    value = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
}

private const val MAX_THUMBS = 3
private const val THUMB_PX = 240
private const val FULL_PX = 1600
