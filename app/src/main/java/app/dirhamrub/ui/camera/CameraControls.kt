package app.dirhamrub.ui.camera

import app.dirhamrub.ui.theme.Springs
import app.dirhamrub.ui.theme.LocalReduceMotion
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.snap
import androidx.camera.core.ZoomState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette
import app.dirhamrub.ui.theme.motion
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.roundToInt

/** Кнопки зума 1× 2× 4× — только те, что поддерживает камера; текущий — подсвечен. */
@Composable
internal fun ZoomChips(zoom: ZoomState?, onZoom: (Float) -> Unit, modifier: Modifier = Modifier, background: Boolean = true) {
    val min = zoom?.minZoomRatio ?: 1f
    val max = zoom?.maxZoomRatio ?: 1f
    val presets = ZOOM_PRESETS.filter { it >= min - 0.01f && it <= max + 0.01f }
    if (presets.size < 2) return
    val ratio = zoom?.zoomRatio ?: 1f
    val current = presets.minBy { abs(it - ratio) }.takeIf { abs(it - ratio) < 0.25f }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(if (background) Modifier.background(Scrim) else Modifier),
    ) {
        for (p in presets) {
            val selected = p == current
            val label = "${p.roundToInt()}×"
            val desc = stringResource(R.string.camera_zoom_a11y, label)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(selected = selected, role = Role.RadioButton) { onZoom(p) }
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = if (selected) CardPalette.star else Color.White,
                    )
                }
            }
        }
    }
}

/** Короткая подсказка под кнопками: появляется и гаснет плавно (без анимации, если она отключена). */
@Composable
internal fun TopToast(text: String?, modifier: Modifier = Modifier) {
    // Пока подсказка гаснет, виден её последний текст.
    var last by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) { if (text != null) last = text }
    val fade by animateFloatAsState(if (text != null) 1f else 0f, motion(180), label = "toast")
    val shown = text ?: last ?: return
    if (fade <= 0f) return
    Text(
        shown,
        style = AppTheme.type.label.copy(fontSize = 13.5.sp),
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .graphicsLayer { alpha = fade }
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Прицел в центре свободной части экрана: цена под ним попадает в фокус. */
@Composable
internal fun Reticle(topReserve: Float, bottomReserve: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, (topReserve + size.height - bottomReserve) / 2f)
        drawCircle(Color.White.copy(alpha = 0.55f), radius = 16.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = center)
    }
}

/** Кольцо фокуса: сжимается к точке касания (без анимации, если она отключена). */
@Composable
internal fun FocusRing(at: Offset) {
    var shown by remember(at) { mutableStateOf(false) }
    LaunchedEffect(at) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 1.4f, motion(200), label = "focus")
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.White, radius = 28.dp.toPx() * scale, center = at, style = Stroke(1.5.dp.toPx()))
    }
}

/** Верхний ряд: назад, карта, режим «в ценнике / плашки». Фонарик, пауза и зум — в панели внизу ([CameraToolbar]). */
@Composable
internal fun TopControls(
    wallet: Wallet,
    viaUnionPay: Boolean,
    embedded: Boolean,
    onBack: () -> Unit,
    onSelectCard: (String) -> Unit,
    onToggleMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(R.drawable.ic_arrow_back, stringResource(R.string.camera_back), onBack)
        Spacer(Modifier.width(6.dp))
        // Карта занимает свободное место; длинное название сокращается, кнопки справа на месте.
        Box(Modifier.weight(1f)) { CardSwitch(wallet, viaUnionPay, onSelectCard) }
        Spacer(Modifier.width(4.dp))
        RoundButton(
            if (embedded) R.drawable.ic_mode_embed else R.drawable.ic_mode_bubbles,
            stringResource(if (embedded) R.string.camera_mode_to_bubbles else R.string.camera_mode_to_embed),
            onToggleMode,
        )
    }
}

/**
 * Плавающая панель камеры внизу (Material 3 Expressive): одна закруглённая «таблетка» — зум 1× 2× 4×, фонарик и
 * пауза. Нажатая кнопка пружинит. На паузе зум прячется (кадр замер).
 */
@Composable
internal fun CameraToolbar(
    zoom: ZoomState?,
    onZoom: (Float) -> Unit,
    torch: Boolean,
    paused: Boolean,
    onTorch: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Scrim)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!paused) {
            ZoomChips(zoom, onZoom, background = false)
        }
        ToolbarButton(
            if (torch) R.drawable.ic_flash_on else R.drawable.ic_flash_off,
            stringResource(if (torch) R.string.camera_torch_off else R.string.camera_torch_on),
            onTorch,
            highlighted = torch,
        )
        ToolbarButton(
            if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            stringResource(if (paused) R.string.camera_resume else R.string.camera_pause),
            onPause,
            highlighted = paused,
        )
    }
}

@Composable
private fun ToolbarButton(icon: Int, description: String, onClick: () -> Unit, highlighted: Boolean) {
    val colors = AppTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, if (reduceMotion) snap() else Springs.fastSpatial(), label = "toolbarPress")
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (highlighted) colors.accent else Color.Transparent)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun RoundButton(icon: Int, description: String, onClick: () -> Unit, highlighted: Boolean = false) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (highlighted) colors.accent else Scrim)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Каким способом считать метки: название выбранного; касание — список всех способов, выбрать любой
 * (лучший — снова автовыбор). Без карт — «ЦБ» (или «UnionPay»).
 */
@Composable
internal fun CardSwitch(wallet: Wallet, viaUnionPay: Boolean, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    val card = wallet.pick(null)
    val title = card?.name ?: stringResource(if (viaUnionPay) R.string.camera_unionpay else R.string.camera_official)
    val desc = stringResource(R.string.camera_card_switch, title)
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(Scrim)
                .clickable(role = Role.Button, enabled = wallet.usable.size > 1) { open = true }
                .semantics { contentDescription = desc }
                .padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = AppTheme.type.bodyStrong,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (wallet.usable.size > 1) {
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            for (c in wallet.usable) {
                DropdownMenuItem(
                    text = {
                        Text(
                            c.name + if (c.id == card?.id) "  ✓" else "",
                            style = AppTheme.type.body,
                            color = if (c.id == card?.id) colors.accent else colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(c.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun BottomStrip(
    basket: List<String>,
    hint: String?,
    spend: Currency,
    home: Currency,
    homePerUnit: BigDecimal?,
    onToCalculator: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (basket.isNotEmpty()) {
            val total = basket.fold(BigDecimal.ZERO) { acc, s -> acc.add(Numbers.parse(s) ?: BigDecimal.ZERO, MC) }
            val homeTotal = homePerUnit?.let { total.multiply(it, MC) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.camera_basket, basket.size, Numbers.money(total), spend.mark),
                        style = type.label,
                        color = colors.muted,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.camera_basket_total, homeTotal?.let(Numbers::moneyWhole) ?: "—", home.mark),
                        style = type.number.copy(fontSize = 20.sp, lineHeight = 26.sp),
                        color = colors.ink,
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.camera_basket_clear),
                        tint = colors.muted,
                    )
                }
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent)
                        .clickable(role = Role.Button, onClick = onToCalculator)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.camera_basket_to_calc), style = type.bodyStrong, color = colors.onAccent)
                }
            }
        } else if (hint != null) {
            Text(
                hint,
                style = type.bodyStrong,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Scrim)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
