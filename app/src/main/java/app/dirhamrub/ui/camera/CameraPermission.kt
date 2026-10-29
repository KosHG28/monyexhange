package app.dirhamrub.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme

/**
 * Показывает [content], только если есть доступ к камере. Иначе — экран с объяснением;
 * если доступ запрещён навсегда — кнопка «Открыть настройки».
 */
@Composable
fun CameraPermissionGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        val activity = context.findActivity()
        // Отказ без возможности снова показать диалог — значит «больше не спрашивать».
        permanentlyDenied = !ok && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    // Вернулись из настроек — проверяем заново.
    LifecycleResumeEffect(Unit) {
        granted = hasCamera(context)
        if (granted) permanentlyDenied = false
        onPauseOrDispose { }
    }

    if (granted) {
        content()
    } else {
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.camera_back), tint = colors.ink)
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = colors.accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.permission_title),
                style = type.title,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (permanentlyDenied) R.string.permission_denied else R.string.permission_text),
                style = type.body,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequest,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    stringResource(if (permanentlyDenied) R.string.permission_settings else R.string.permission_allow),
                    style = type.bodyStrong,
                )
            }
        }
    }
}

private fun hasCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
