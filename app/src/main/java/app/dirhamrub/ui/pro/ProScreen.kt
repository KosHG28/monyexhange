package app.dirhamrub.ui.pro

import androidx.compose.animation.core.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.Springs
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.ui.components.ScreenBar
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import app.dirhamrub.ui.theme.topLight

private class Feature(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val sub: Int)

private val FEATURES = listOf(
    Feature(R.drawable.ic_widget, R.string.pro_f_widget, R.string.pro_f_widget_sub),
    Feature(R.drawable.ic_scan, R.string.pro_f_shots, R.string.pro_f_shots_sub),
    Feature(R.drawable.ic_shortcuts, R.string.pro_f_shortcuts, R.string.pro_f_shortcuts_sub),
    Feature(R.drawable.ic_palette, R.string.pro_f_colors, R.string.pro_f_colors_sub),
    Feature(R.drawable.ic_app_icon, R.string.pro_f_icon, R.string.pro_f_icon_sub),
)

/** «Курс по чеку Pro»: возможности и временная активация общим промокодом без оплаты. */
@Composable
fun ProScreen(pro: Boolean, onActivate: (String) -> Boolean, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val haptics = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var code by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var celebrated by rememberSaveable { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    val celebration = remember { Animatable(1f) }
    LaunchedEffect(pro, celebrated, reduce) {
        if (pro && celebrated && !reduce) {
            celebration.snapTo(0f)
            celebration.animateTo(1f, tween(700))
        } else celebration.snapTo(1f)
    }
    val buttonFraction by animateFloatAsState(if (pro) 0.78f else 1f, if (reduce) snap() else Springs.fastSpatial(), label = "proButtonShape")
    val buttonCorner by animateDpAsState(if (pro) 26.dp else 14.dp, if (reduce) snap() else spring(), label = "proCorner")
    val activate = {
        if (!pro && code.isNotBlank()) {
            if (onActivate(code)) {
                celebrated = true
                invalid = false
                code = ""
                keyboard?.hide()
                focus.clearFocus()
                haptics.play(Haptic.CONFIRM)
            } else {
                invalid = true
                haptics.play(Haptic.REJECT)
            }
        }
    }
    FullScreen(onDismissRequest = onDismiss) {
        val wave = rememberCascade()
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenBar(onBack = { close() })
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadingScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_app_icon), contentDescription = null, tint = if (pro) colors.good else colors.accent,
                        modifier = Modifier.size(34.dp).graphicsLayer {
                            val p = celebration.value
                            rotationZ = if (pro && celebrated) -12f * (1f - p) else 0f
                            scaleX = 1f + if (pro && celebrated) kotlin.math.sin(p * Math.PI).toFloat() * 0.15f else 0f
                            scaleY = scaleX
                        })
                Text(
                    stringResource(R.string.pro_title),
                    style = type.title.copy(fontSize = 30.sp, lineHeight = 36.sp),
                    color = colors.ink,
                    modifier = Modifier.cascade(wave, 0).padding(start = 4.dp).semantics { heading() },
                )
                }
                Text(
                    stringResource(R.string.pro_lead),
                    style = type.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
                    color = colors.muted,
                    modifier = Modifier.cascade(wave, 1).padding(start = 4.dp, top = 6.dp, bottom = 16.dp),
                )
                Column(
                    Modifier
                        .cascade(wave, 2)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .topLight(),
                ) {
                    FEATURES.forEachIndexed { i, f ->
                        if (i > 0) {
                            Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(colors.line))
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Icon(painterResource(f.icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(stringResource(f.title), style = type.body.copy(fontWeight = FontWeight.Medium), color = colors.ink)
                                Text(stringResource(f.sub), style = type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp), color = colors.muted)
                            }
                        }
                    }
                }
                if (!pro) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; invalid = false },
                        label = { Text(stringResource(R.string.pro_promo_label)) },
                        singleLine = true,
                        isError = invalid,
                        supportingText = {
                            Text(stringResource(if (invalid) R.string.pro_promo_invalid else R.string.pro_promo_hint))
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { activate() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.ink,
                            unfocusedTextColor = colors.ink,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.line,
                            focusedLabelColor = colors.accent,
                            unfocusedLabelColor = colors.muted,
                            cursorColor = colors.accent,
                            errorBorderColor = colors.danger,
                            errorLabelColor = colors.danger,
                            errorSupportingTextColor = colors.danger,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val label = stringResource(if (pro) R.string.pro_active else R.string.pro_promo_activate)
                val enabled = !pro && code.isNotBlank()
                Box(
                    Modifier
                        .fillMaxWidth(buttonFraction)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(buttonCorner))
                        .drawWithContent {
                            drawContent()
                            if (pro && celebrated && celebration.value < 1f) {
                                val p = celebration.value
                                drawRect(colors.good.copy(alpha = (1f - p) * 0.18f), topLeft = androidx.compose.ui.geometry.Offset(size.width * p - size.width / 4, 0f), size = androidx.compose.ui.geometry.Size(size.width / 4, size.height))
                            }
                        }
                        .background(if (pro) colors.goodSoft else if (enabled) colors.accent else colors.surface2)
                        .clickable(enabled = enabled, role = Role.Button, onClick = activate),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = type.bodyStrong.copy(fontSize = 16.sp), color = if (pro) colors.good else if (enabled) colors.onAccent else colors.muted)
                }
            }
        }
    }
}
