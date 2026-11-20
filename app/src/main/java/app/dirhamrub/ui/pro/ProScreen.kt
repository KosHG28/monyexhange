package app.dirhamrub.ui.pro

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.LauncherPreview
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
    cards: List<BankCard>,
    onPureBlack: (Boolean) -> Unit,
    onStyles: () -> Unit,
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
                    FeatureRow(stringResource(R.string.pro_f_tile), stringResource(R.string.pro_f_tile_sub),
                        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                        onClick = if (pro) ({ if (!ProFeatures.requestCameraTile(context)) help = ProHelp.TILE }) else null,
                    ) { TilePreview() }
                }
                Spacer(Modifier.height(12.dp))
                StylePreview(settings, cards, onPureBlack, onStyles, onAppIcon)
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
                title = { Text(stringResource(if (topic == ProHelp.WIDGET) R.string.pro_f_widget else R.string.pro_f_tile)) },
                text = { Text(stringResource(if (topic == ProHelp.WIDGET) R.string.pro_widget_help else R.string.pro_tile_help)) },
                confirmButton = { TextButton(onClick = { help = null }) { Text(stringResource(R.string.pro_help_close)) } },
                containerColor = colors.surface,
                titleContentColor = colors.ink,
                textContentColor = colors.ink,
            )
        }
    }
}

private enum class ProHelp { WIDGET, TILE }

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
private fun TilePreview() {
    val description = stringResource(R.string.pro_tile_preview)
    Box(Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(14.dp))
        .background(AppTheme.colors.accentSoft).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_camera_white), null, tint = AppTheme.colors.accent, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun StylePreview(
    settings: AppSettings, cards: List<BankCard>, onPureBlack: (Boolean) -> Unit,
    onStyles: () -> Unit, onAppIcon: (AppIcon) -> Unit,
) {
    val colors = AppTheme.colors
    val fontScale = LocalDensity.current.fontScale
    ProSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.pro_your_style), style = AppTheme.type.title, color = colors.ink)
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 270.dp || fontScale > 1.4f) {
                    Column {
                        AmoledOption(settings, onPureBlack, Modifier.fillMaxWidth())
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.line)
                        CardColorsOption(settings.pro, cards, onStyles, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        AmoledOption(settings, onPureBlack, Modifier.weight(1f).fillMaxHeight())
                        VerticalDivider(Modifier.padding(horizontal = 12.dp).fillMaxHeight(), color = colors.line)
                        CardColorsOption(settings.pro, cards, onStyles, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.line)
            Text(stringResource(R.string.pro_f_icon), style = AppTheme.type.body.copy(fontSize = 13.sp), color = colors.ink)
            Spacer(Modifier.height(8.dp))
            IconOptions(settings, onAppIcon)
        }
    }
}

@Composable
private fun AmoledOption(settings: AppSettings, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val label = stringResource(R.string.pro_amoled)
    val background by animateColorAsState(if (settings.pureBlack) androidx.compose.ui.graphics.Color.Black else colors.proPalette.amoledOff,
        motion(180), label = "amoledPreview")
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = AppTheme.type.body.copy(fontSize = 13.sp, lineHeight = 17.sp), color = colors.ink, modifier = Modifier.weight(1f))
                Switch(checked = settings.pureBlack, onCheckedChange = onChange, enabled = settings.pro,
                    modifier = Modifier.semantics { contentDescription = label },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.onAccent, checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.muted, uncheckedTrackColor = colors.surface2, uncheckedBorderColor = colors.line))
            }
            Text(stringResource(R.string.pro_amoled_sub), style = AppTheme.type.caption, color = colors.muted)
        }
        Row(Modifier.padding(top = 10.dp).height(38.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(background).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(colors.goodSoft))
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(colors.surface2))
        }
    }
}

@Composable
private fun CardColorsOption(pro: Boolean, cards: List<BankCard>, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = pro, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pro_card_colors), style = AppTheme.type.body.copy(fontSize = 13.sp, lineHeight = 17.sp), color = colors.ink, modifier = Modifier.weight(1f))
                Text("›", color = colors.muted, fontSize = 22.sp)
            }
            Text(stringResource(R.string.pro_per_card), style = AppTheme.type.caption, color = colors.muted)
        }
        Row(Modifier.padding(top = 10.dp).height(38.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((slot, index) in listOf(0, 1, 3, 6).withIndex()) {
                val style = cards.filter { it.kind == app.dirhamrub.domain.wallet.CardKind.CARD }.getOrNull(slot)?.let(CardPalette::style) ?: CardPalette.style(index)
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                    .background(Brush.linearGradient(listOf(style.start, style.end)))) {
                    Box(Modifier.padding(6.dp).size(7.dp, 6.dp).clip(RoundedCornerShape(2.dp)).background(style.chip))
                }
            }
        }
    }
}

@Composable
private fun IconOptions(settings: AppSettings, onChange: (AppIcon) -> Unit) {
    val colors = AppTheme.colors
    val icons = listOf(AppIcon.CLASSIC, AppIcon.GOLD, AppIcon.EMERALD, AppIcon.PLATINUM)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Accessibility text gets two equal rows rather than clipped labels or tiny touch targets.
        val columns = if (fontScale > 1.2f || maxWidth < 254.dp) 2 else 4
        val compact = maxWidth < 280.dp
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in icons.chunked(columns)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (variant in row) {
                    val selected = settings.appIcon == variant
                    val bg by animateColorAsState(if (selected) colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.10f) else colors.proPalette.tileSurface, motion(180), label = "iconTileBg")
                    val outline by animateColorAsState(if (selected) colors.accent else Color.Transparent, motion(180), label = "iconTileOutline")
                    val ink by animateColorAsState(if (selected) colors.accent else colors.muted, motion(180), label = "iconTileInk")
                    val title = stringResource(when (variant) {
                        AppIcon.CLASSIC -> R.string.pro_icon_classic
                        AppIcon.GOLD, AppIcon.DARK -> R.string.pro_icon_gold
                        AppIcon.EMERALD -> R.string.pro_icon_emerald
                        AppIcon.PLATINUM -> R.string.pro_icon_platinum
                    })
                    val shape = RoundedCornerShape(14.dp)
                    Column(Modifier.weight(1f).clip(shape).background(bg).border(1.5.dp, outline, shape)
                        .selectable(selected, enabled = settings.pro, role = Role.RadioButton) { onChange(variant) }
                        .semantics { contentDescription = title }.padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LauncherPreview(variant)
                        Spacer(Modifier.height(7.dp))
                        Text(title, color = ink, style = AppTheme.type.caption.copy(fontSize = if (compact) 11.sp else 11.5.sp,
                            letterSpacing = if (compact) (-0.3).sp else 0.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
