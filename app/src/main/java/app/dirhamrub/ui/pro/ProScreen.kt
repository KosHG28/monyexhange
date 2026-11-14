package app.dirhamrub.ui.pro

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import app.dirhamrub.domain.AppSettings
import app.dirhamrub.domain.AppIcon
import app.dirhamrub.data.ProFeatures
import app.dirhamrub.ui.theme.proPalette
import app.dirhamrub.ui.theme.motion
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.ui.components.ScreenBar
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics

/** Возможности Pro: реальные превью, обычная палитра приложения и активация промокодом. */
@Composable
fun ProScreen(
    settings: AppSettings,
    onDynamicColor: (Boolean) -> Unit,
    onAppIcon: (AppIcon) -> Unit,
    onActivate: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val pro = settings.pro
    val context = LocalContext.current
    var help by rememberSaveable { mutableStateOf<ProHelp?>(null) }
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
                    Text(stringResource(R.string.pro_badge).uppercase(), style = type.bodyStrong, color = colors.accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent.copy(alpha = 0.16f))
                            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(stringResource(R.string.pro_lead), style = type.body, color = colors.muted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                ProSurface {
                    FeatureRow(stringResource(R.string.pro_f_widget), stringResource(R.string.pro_f_widget_sub),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        onClick = if (pro) ({
                            if (!ProFeatures.requestWidget(context)) help = ProHelp.WIDGET
                        }) else null,
                    ) { WidgetPreview() }
                    FeatureDivider()
                    FeatureRow(stringResource(R.string.pro_f_shots), stringResource(R.string.pro_f_shots_sub)) { ReceiptPreview() }
                    FeatureDivider()
                    FeatureRow(stringResource(R.string.pro_f_shortcuts), stringResource(R.string.pro_f_shortcuts_sub),
                        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                        onClick = if (pro) ({ help = ProHelp.SHORTCUTS }) else null,
                    ) { ShortcutPreview() }
                }
                Spacer(Modifier.height(12.dp))
                StylePreview(settings, onDynamicColor, onAppIcon)
                Spacer(Modifier.height(16.dp))
                if (pro) {
                    ActiveProBanner()
                } else ProSurface {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
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
                        val enabled = code.isNotBlank()
                        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (enabled) colors.accent else colors.surface2)
                            .clickable(enabled = enabled, role = Role.Button, onClick = activate),
                            contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.pro_promo_activate),
                                style = type.bodyStrong.copy(fontSize = 16.sp),
                                color = if (enabled) colors.onAccent else colors.muted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        }
        help?.let { topic ->
            AlertDialog(
                onDismissRequest = { help = null },
                title = { Text(stringResource(if (topic == ProHelp.WIDGET) R.string.pro_f_widget else R.string.pro_f_shortcuts)) },
                text = { Text(stringResource(if (topic == ProHelp.WIDGET) R.string.pro_widget_help else R.string.pro_shortcuts_help)) },
                confirmButton = { TextButton(onClick = { help = null }) { Text(stringResource(R.string.pro_help_close)) } },
                containerColor = colors.surface,
                titleContentColor = colors.ink,
                textContentColor = colors.ink,
            )
        }
    }
}

private enum class ProHelp { WIDGET, SHORTCUTS }

@Composable
private fun ActiveProBanner() {
    val palette = AppTheme.colors.proPalette
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(palette.activeSurface)
        .border(1.dp, palette.activeOutline, shape).heightIn(min = 56.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = palette.activeInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.pro_active), style = AppTheme.type.bodyStrong, color = palette.activeInk)
    }
}

@Composable
private fun ProSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AppTheme.colors.surface), content = content)
}

@Composable
private fun FeatureDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.colors.line)
}

@Composable
private fun FeatureRow(
    title: String, sub: String, shape: Shape = RoundedCornerShape(0.dp),
    onClick: (() -> Unit)? = null, preview: @Composable () -> Unit,
) {
    val action = if (onClick != null) Modifier.clip(shape).clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(Modifier.fillMaxWidth().then(action).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val palette = colors.proPalette
    Column(Modifier.width(76.dp).clip(ReceiptPreviewShape).background(palette.receiptSurface)
        .border(1.dp, palette.receiptOutline, ReceiptPreviewShape).padding(horizontal = 9.dp, vertical = 10.dp).clearAndSetSemantics { contentDescription = description }) {
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
private fun StylePreview(settings: AppSettings, onDynamicColor: (Boolean) -> Unit, onAppIcon: (AppIcon) -> Unit) {
    val colors = AppTheme.colors
    val fontScale = LocalDensity.current.fontScale
    ProSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.pro_your_style), style = AppTheme.type.title, color = colors.ink)
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Three 48 dp icon targets need 144 dp; stack rather than shrink them on narrow screens.
                if (maxWidth < 313.dp || fontScale > 1.4f) {
                    Column {
                        WallpaperOption(settings, onDynamicColor, Modifier.fillMaxWidth())
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.line)
                        IconOption(settings, onAppIcon, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        WallpaperOption(settings, onDynamicColor, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), color = colors.line)
                        IconOption(settings, onAppIcon, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperOption(settings: AppSettings, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val label = stringResource(R.string.pro_f_colors)
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = AppTheme.type.body.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = colors.ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.pro && settings.dynamicColor,
                    onCheckedChange = onChange,
                    enabled = settings.pro && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    modifier = Modifier.semantics { contentDescription = label },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent, uncheckedThumbColor = colors.muted,
                        uncheckedTrackColor = colors.surface2, uncheckedBorderColor = colors.line),
                )
            }
            Text(stringResource(R.string.pro_android_version), style = AppTheme.type.caption, color = colors.muted)
        }
        Row(Modifier.padding(top = 12.dp).height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            for (color in listOf(colors.accent, colors.accent.copy(alpha = 0.45f), colors.accent.copy(alpha = 0.18f))) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color))
            }
        }
    }
}

@Composable
private fun IconOption(settings: AppSettings, onChange: (AppIcon) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.pro_f_icon), style = AppTheme.type.body.copy(fontSize = 13.sp, lineHeight = 17.sp), color = colors.ink)
        }
        Row(Modifier.padding(top = 12.dp).height(48.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            for (variant in AppIcon.entries) {
                val selected = settings.appIcon == variant
                val scale by animateFloatAsState(if (selected) 1.06f else 1f, motion(180), label = "proIconScale")
                val outline by animateColorAsState(if (selected) colors.accent else Color.Transparent, motion(180), label = "proIconOutline")
                val shape = RoundedCornerShape(12.dp)
                val background = when (variant) {
                    AppIcon.CLASSIC -> R.color.ic_launcher_background
                    AppIcon.DARK -> R.color.ic_launcher_background_dark
                    AppIcon.GOLD -> R.color.ic_launcher_background_gold
                }
                val title = stringResource(when (variant) {
                    AppIcon.CLASSIC -> R.string.app_icon_classic
                    AppIcon.DARK -> R.string.app_icon_dark
                    AppIcon.GOLD -> R.string.app_icon_gold
                })
                Box(Modifier.size(48.dp).clip(shape)
                    .selectable(selected = selected, enabled = settings.pro, role = Role.RadioButton) { onChange(variant) }
                    .semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(shape).background(colorResource(background)).border(1.5.dp, outline, shape)) {
                        Image(painterResource(if (variant == AppIcon.GOLD) R.drawable.ic_launcher_foreground_gold else R.drawable.ic_launcher_foreground),
                            contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
