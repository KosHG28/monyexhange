package app.dirhamrub.ui.pro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.*
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.ui.components.ScreenBar
import app.dirhamrub.ui.main.BankCardView
import app.dirhamrub.ui.main.MainUiState
import app.dirhamrub.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A local visual draft; applying rebases it onto the latest saved card in MainViewModel. */
@Composable
internal fun CardStyleScreen(state: MainUiState, onApply: (String, Int, Int?) -> Unit, onDismiss: () -> Unit) {
    val cards = state.wallet.cards.filter { it.kind == CardKind.CARD }
    var chosenId by rememberSaveable { mutableStateOf(state.selectedCard?.id ?: cards.firstOrNull()?.id) }
    val card = cards.firstOrNull { it.id == chosenId } ?: cards.firstOrNull()
    val colors = AppTheme.colors
    FullScreen(onDismissRequest = onDismiss) {
        var applied by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(applied) {
            if (applied) {
                delay(1_000)
                close()
            }
        }
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenBar(onBack = { close() }, title = stringResource(R.string.style_title))
            if (card == null) {
                Text(stringResource(R.string.style_empty), style = AppTheme.type.body, color = colors.muted, modifier = Modifier.padding(24.dp))
            } else {
                val savedPreset = Math.floorMod(card.style, Wallet.CARD_STYLES)
                var preset by rememberSaveable(card.id, card.style, card.customColor) { mutableIntStateOf(savedPreset) }
                var custom by rememberSaveable(card.id, card.style, card.customColor) { mutableStateOf(card.customColor != null) }
                var customOpen by rememberSaveable(card.id) { mutableStateOf(false) }
                var hex by rememberSaveable(card.id, card.customColor) { mutableStateOf(CardAppearance.hex(card.customColor ?: (CardPalette.style(card).start.toArgb() and 0xFFFFFF))) }
                val rgb = CardAppearance.parseHex(hex)
                val draft = card.copy(style = preset, customColor = if (custom) rgb ?: card.customColor else null)
                val changed = if (draft.customColor != null || card.customColor != null) {
                    draft.customColor != card.customColor
                } else preset != savedPreset
                val q = state.quote.of(card.id)
                val sum = q?.let { Numbers.total(state.totals.spend.multiply(it.homePerUnit, MC), state.settings.rounding) }
                val focus = LocalFocusManager.current
                var previewVisible by remember { mutableStateOf(false) }
                val reflectionTilt = rememberReflectionTilt(previewVisible && !isClosing)
                val reduce = LocalReduceMotion.current
                var sweepRequest by remember(card.id) { mutableIntStateOf(0) }
                var playedSweep by remember(card.id) { mutableIntStateOf(0) }
                val sweep = remember(card.id) { Animatable(1f) }
                LaunchedEffect(sweepRequest, reduce, isClosing) {
                    if (sweepRequest == playedSweep || reduce || isClosing) {
                        playedSweep = sweepRequest
                        sweep.snapTo(1f)
                    } else {
                        playedSweep = sweepRequest
                        sweep.snapTo(0f)
                        sweep.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
                    }
                }
                HighFrameRate(sweep.isRunning)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardChoice(cards, card, { if (!applied) chosenId = it })
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        .onGloballyPositioned { previewVisible = !it.boundsInWindow().isEmpty }
                        .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                        BankCardView(draft, q, state.wallet.spend, state.wallet.home, sum, true, null, {}, {},
                            width = 270.dp, height = 142.dp, flipOnChange = false,
                            reflectionTilt = reflectionTilt, colorSweep = sweep.asState())
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (customOpen) R.string.style_custom else R.string.style_colors), style = AppTheme.type.bodyStrong,
                                color = colors.ink, modifier = Modifier.weight(1f))
                            if (customOpen) TextButton(onClick = { focus.clearFocus(); customOpen = false }) { Text(stringResource(R.string.style_ready), color = colors.accent) }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (customOpen) {
                            CustomColorEditor(hex, { input ->
                                if (!applied) {
                                    hex = input
                                    val presetRgb = CardPalette.style(preset).start.toArgb() and 0xFFFFFF
                                    val entered = CardAppearance.parseHex(input)
                                    custom = entered != presetRgb || card.customColor == entered
                                }
                            }, rgb)
                        } else {
                            ColorPresets(preset, custom, {
                                if (!applied && (custom || preset != it)) {
                                    preset = it
                                    custom = false
                                    sweepRequest++
                                }
                            }, {
                                if (!applied) {
                                    if (!custom) hex = CardAppearance.hex(CardPalette.style(preset).start.toArgb() and 0xFFFFFF)
                                    customOpen = true
                                }
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.style_main_preview), style = AppTheme.type.caption, color = colors.muted, modifier = Modifier.padding(bottom = 8.dp))
                    AppearanceRow(draft, sum, state.wallet.home)
                }
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    val enabled = state.settings.pro && changed && (!custom || rgb != null) && !applied
                    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                        .background(when { applied -> colors.goodSoft; enabled -> colors.accent; else -> colors.surface2 })
                        .clickable(enabled, role = Role.Button) {
                            if (!applied) {
                                applied = true
                                focus.clearFocus()
                                onApply(card.id, preset, if (custom) rgb else null)
                            }
                        }
                        .semantics { if (applied) liveRegion = LiveRegionMode.Polite }
                        .padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (applied) R.string.style_applied else R.string.style_apply),
                            style = AppTheme.type.bodyStrong.copy(fontSize = 17.sp),
                            color = when { applied -> colors.good; enabled -> colors.onAccent; else -> colors.muted })
                    }
                }
            }
        }
    }
}

@Composable
private fun CardChoice(cards: List<BankCard>, current: BankCard, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    val label = stringResource(R.string.style_card_picker)
    Box {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .clickable(role = Role.Button) { open = true }.semantics { contentDescription = label }
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniColorCard(current)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(current.name, style = AppTheme.type.bodyStrong, color = colors.ink)
                Text(current.currency.code, style = AppTheme.type.caption, color = colors.muted)
            }
            Text("⌄", color = colors.muted, fontSize = 20.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (card in cards) DropdownMenuItem(text = { Text(card.name) }, onClick = { open = false; onPick(card.id) })
        }
    }
}

private val PRESET_NAMES = listOf(R.string.style_teal, R.string.style_bordeaux, R.string.style_indigo,
    R.string.style_emerald, R.string.style_amber, R.string.style_purple, R.string.style_graphite)

@Composable
private fun ColorPresets(preset: Int, custom: Boolean, onPreset: (Int) -> Unit, onCustom: () -> Unit) {
    val colors = AppTheme.colors
    for (row in (0..7).chunked(4)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in row) {
                val own = index == 7
                val selected = if (own) custom else !custom && preset == index
                val label = stringResource(if (own) R.string.style_custom else PRESET_NAMES[index])
                val bg by animateColorAsState(if (selected) colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.10f) else colors.proPalette.tileSurface, motion(180), label = "colorTile")
                val border by animateColorAsState(if (selected) colors.accent else Color.Transparent, motion(180), label = "colorTileBorder")
                val shape = RoundedCornerShape(14.dp)
                Column(Modifier.weight(1f).clip(shape).background(bg).border(1.5.dp, border, shape)
                    .selectable(selected, role = Role.RadioButton) { if (own) onCustom() else onPreset(index) }
                    .semantics { contentDescription = label }.padding(horizontal = 3.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val style = CardPalette.style(index)
                    Box(Modifier.size(50.dp, 32.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (own) Brush.horizontalGradient(CardPalette.hueStops) else Brush.linearGradient(listOf(style.start, style.end))), contentAlignment = Alignment.Center) {
                        if (own) Text("+", fontSize = 20.sp, color = Color.White)
                        else Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(7.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(style.chip))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(label, color = if (selected) colors.accent else colors.muted, style = AppTheme.type.caption.copy(fontSize = 11.sp,
                        letterSpacing = (-0.3).sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, softWrap = false)
                }
            }
        }
        if (row.last() != 7) Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomColorEditor(hex: String, onHex: (String) -> Unit, rgb: Int?) {
    val colors = AppTheme.colors
    val hsv = remember(rgb) { FloatArray(3).also { android.graphics.Color.colorToHSV(0xFF000000.toInt() or (rgb ?: 0x9858CB), it) } }
    var sliderHue by remember { mutableStateOf<Float?>(null) }
    val hue = sliderHue ?: hsv[0]
    val focus = LocalFocusManager.current
    // Keep saturation/value from the current valid draft; no money is converted to Float.
    fun choose(h: Float, s: Float, v: Float) { onHex(CardAppearance.hex(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) and 0xFFFFFF)) }
    val hueColor = Color.hsv(hue, 1f, 1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { 116.dp.toPx() }
        val radius = with(density) { 7.dp.toPx() }
        val pickColor by rememberUpdatedState<(Offset) -> Unit>({ pos ->
            choose(hue, (pos.x / width).coerceIn(0f, 1f), 1f - (pos.y / height).coerceIn(0f, 1f))
        })
        Box(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(12.dp))
            .background(hueColor)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .pointerInput(width, height) {
                detectTapGestures { pos -> pickColor(pos) }
            }
            .pointerInput(width, height) {
                detectDragGestures(onDragStart = { pos -> pickColor(pos) }) { change, _ ->
                    change.consume(); pickColor(change.position)
                }
            }) {
            Box(Modifier.offset { IntOffset((hsv[1] * width - radius).roundToInt(), ((1f - hsv[2]) * height - radius).roundToInt()) }
                .size(14.dp).border(2.dp, Color.White, RoundedCornerShape(50)))
        }
    }
    val description = stringResource(R.string.style_hue)
    Slider(
        value = hue, onValueChange = {
            sliderHue = it
            choose(it, hsv[1], hsv[2])
        }, valueRange = 0f..359.9f,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp).semantics { contentDescription = description },
        thumb = {
            Box(Modifier.size(23.dp).shadow(3.dp, CircleShape).background(hueColor, CircleShape)
                .border(2.dp, Color.White, CircleShape))
        },
        track = {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Brush.horizontalGradient(HUE_TRACK)))
        },
    )
    OutlinedTextField(value = hex, onValueChange = { sliderHue = null; onHex(it.take(7)) }, singleLine = true,
        label = { Text(stringResource(R.string.style_hex)) }, isError = rgb == null,
        supportingText = if (rgb == null) ({ Text(stringResource(R.string.style_hex_error)) }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
}

private val HUE_TRACK = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

@Composable
private fun MiniColorCard(card: BankCard) {
    val style = CardPalette.style(card)
    Box(Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(listOf(style.start, style.end)))) {
        Box(Modifier.padding(6.dp).size(7.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(style.chip))
        Text(card.currency.code, color = style.ink, style = AppTheme.type.caption.copy(fontSize = 7.sp), modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
    }
}

@Composable
private fun AppearanceRow(card: BankCard, sum: String?, home: Currency) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(colors.payRowSurface(true, false)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniColorCard(card)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(card.name, style = AppTheme.type.bodyStrong, color = colors.ink, maxLines = 1)
            Text(card.currency.code, style = AppTheme.type.caption, color = colors.muted)
        }
        Text(sum?.let { "≈ $it ${home.mark}" } ?: "—", style = AppTheme.type.bodyStrong.copy(fontSize = 13.sp), color = colors.accent)
    }
}
