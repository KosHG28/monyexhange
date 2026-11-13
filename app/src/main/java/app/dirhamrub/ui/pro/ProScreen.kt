package app.dirhamrub.ui.pro

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.ui.components.ScreenBar
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics

/** Возможности Pro: реальные превью, обычная палитра приложения и активация промокодом. */
@Composable
fun ProScreen(pro: Boolean, onActivate: (String) -> Boolean, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val haptics = rememberHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var code by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val activate = {
        if (!pro && code.isNotBlank()) {
            if (onActivate(code)) {
                invalid = false
                code = ""
                keyboard?.hide()
                focus.clearFocus(force = true)
                haptics.play(Haptic.CONFIRM)
            } else {
                invalid = true
                haptics.play(Haptic.REJECT)
            }
        }
    }
    FullScreen(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenBar(onBack = { close() })
            // Единая прокрутка сохраняет доступ к активации на коротком экране и при крупном шрифте.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pro_brand_title), style = type.title.copy(fontSize = 30.sp, lineHeight = 36.sp),
                        color = colors.ink, modifier = Modifier.weight(1f, fill = false).semantics { heading() })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pro_badge).uppercase(), style = type.bodyStrong, color = colors.onAccent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(stringResource(R.string.pro_lead), style = type.body, color = colors.muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                ProSurface {
                    FeatureRow(stringResource(R.string.pro_f_widget), stringResource(R.string.pro_f_widget_sub)) { WidgetPreview() }
                    FeatureDivider()
                    FeatureRow(stringResource(R.string.pro_f_shots), stringResource(R.string.pro_f_shots_sub)) { ReceiptPreview() }
                    FeatureDivider()
                    FeatureRow(stringResource(R.string.pro_f_shortcuts), stringResource(R.string.pro_f_shortcuts_sub)) { ShortcutPreview() }
                }
                Spacer(Modifier.height(12.dp))
                StylePreview()
                Spacer(Modifier.height(16.dp))
                ProSurface {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (!pro) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it; invalid = false },
                                label = { Text(stringResource(R.string.pro_promo_label)) },
                                singleLine = true,
                                isError = invalid,
                                supportingText = if (invalid) ({ Text(stringResource(R.string.pro_promo_invalid)) }) else null,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters,
                                    autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { activate() }),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
                                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.line,
                                    focusedLabelColor = colors.accent, unfocusedLabelColor = colors.muted,
                                    cursorColor = colors.accent, errorBorderColor = colors.danger,
                                    errorLabelColor = colors.danger, errorSupportingTextColor = colors.danger,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        val enabled = !pro && code.isNotBlank()
                        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (pro) colors.goodSoft else if (enabled) colors.accent else colors.surface2)
                            .clickable(enabled = enabled, role = Role.Button, onClick = activate),
                            contentAlignment = Alignment.Center) {
                            Text(stringResource(if (pro) R.string.pro_active else R.string.pro_promo_activate),
                                style = type.bodyStrong.copy(fontSize = 16.sp),
                                color = if (pro) colors.good else if (enabled) colors.onAccent else colors.muted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppTheme.colors.surface), content = content)
}

@Composable
private fun FeatureDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(AppTheme.colors.line))
}

@Composable
private fun FeatureRow(title: String, sub: String, preview: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium), color = AppTheme.colors.ink)
            Text(sub, style = AppTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp), color = AppTheme.colors.muted,
                modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        preview()
    }
}

@Composable
private fun WidgetPreview() {
    val colors = AppTheme.colors
    val description = stringResource(R.string.pro_widget_preview)
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).background(colors.paper)
        .border(1.dp, colors.line, RoundedCornerShape(12.dp)).padding(10.dp)
        .clearAndSetSemantics { contentDescription = description }) {
        Text(stringResource(R.string.pro_preview_place), style = AppTheme.type.caption.copy(fontSize = 10.sp), color = colors.muted)
        Text(stringResource(R.string.pro_preview_sum), style = AppTheme.type.number.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = colors.ink, maxLines = 1)
        Text(stringResource(R.string.pro_preview_card), style = AppTheme.type.caption.copy(fontSize = 11.sp), color = colors.good)
    }
}

@Composable
private fun ReceiptPreview() {
    val colors = AppTheme.colors
    val description = stringResource(R.string.pro_receipt_preview)
    Column(Modifier.width(76.dp).clip(ReceiptPreviewShape).background(colors.surface2)
        .padding(horizontal = 9.dp, vertical = 10.dp).clearAndSetSemantics { contentDescription = description }) {
        Text(stringResource(R.string.pro_preview_receipt), style = AppTheme.type.caption, color = colors.ink)
        Text(stringResource(R.string.pro_preview_receipt_sum), style = AppTheme.type.caption.copy(fontSize = 10.sp), color = colors.ink,
            modifier = Modifier.padding(top = 4.dp))
        repeat(2) { Box(Modifier.padding(top = 5.dp).fillMaxWidth(if (it == 0) 1f else 0.7f).height(2.dp).background(colors.line)) }
        Spacer(Modifier.height(5.dp))
    }
}

private object ReceiptPreviewShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tooth = with(density) { 4.dp.toPx() }
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - tooth)
            for (i in 1..12) lineTo(size.width * (1f - i / 12f), size.height - if (i % 2 == 0) tooth else 0f)
            close()
        })
    }
}

@Composable
private fun ShortcutPreview() {
    val description = stringResource(R.string.pro_shortcuts_preview)
    Row(Modifier.clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (icon in listOf(R.drawable.ic_scan, R.drawable.ic_place_white)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.surface2),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StylePreview() {
    val colors = AppTheme.colors
    ProSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.pro_your_style), style = AppTheme.type.title, color = colors.ink)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pro_f_colors), style = AppTheme.type.body.copy(fontSize = 13.sp), color = colors.ink)
                    Text(stringResource(R.string.pro_android_version), style = AppTheme.type.caption, color = colors.muted,
                        modifier = Modifier.padding(top = 3.dp))
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (color in listOf(colors.accent, colors.accent.copy(alpha = 0.45f), colors.accent.copy(alpha = 0.18f))) {
                            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(color))
                        }
                    }
                }
                Box(Modifier.width(1.dp).height(82.dp).background(colors.line))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pro_f_icon), style = AppTheme.type.body.copy(fontSize = 13.sp), color = colors.ink)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val variants = listOf(
                            R.color.ic_launcher_background to R.drawable.ic_launcher_foreground,
                            R.color.ic_launcher_background_dark to R.drawable.ic_launcher_foreground,
                            R.color.ic_launcher_background_gold to R.drawable.ic_launcher_foreground_gold,
                        )
                        for ((background, foreground) in variants) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(colorResource(background))) {
                                Image(painterResource(foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}
