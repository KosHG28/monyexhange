package app.dirhamrub.ui.pro

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.data.ProBilling
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.ui.components.ScreenBar
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import app.dirhamrub.ui.theme.topLight
import kotlinx.coroutines.launch

private class Feature(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val sub: Int)

private val FEATURES = listOf(
    Feature(R.drawable.ic_widget, R.string.pro_f_widget, R.string.pro_f_widget_sub),
    Feature(R.drawable.ic_scan, R.string.pro_f_shots, R.string.pro_f_shots_sub),
    Feature(R.drawable.ic_shortcuts, R.string.pro_f_shortcuts, R.string.pro_f_shortcuts_sub),
    Feature(R.drawable.ic_palette, R.string.pro_f_colors, R.string.pro_f_colors_sub),
    Feature(R.drawable.ic_app_icon, R.string.pro_f_icon, R.string.pro_f_icon_sub),
)

/**
 * «Курс по чеку Pro»: что даёт (виджет, скриншоты при чеках, ярлыки, цвета из обоев, значок) и разовая покупка.
 * Пока подключена тестовая заглушка ([ProBilling.isTest]): оплаты нет, покупку можно отменить здесь же.
 */
@Composable
fun ProScreen(pro: Boolean, billing: ProBilling, onBought: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var buying by remember { mutableStateOf(false) }
    FullScreen(onDismissRequest = onDismiss) {
        val wave = rememberCascade()
        Column(Modifier.fillMaxSize()) {
            ScreenBar(onBack = { close() })
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadingScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(R.string.pro_title),
                    style = type.title.copy(fontSize = 30.sp, lineHeight = 36.sp),
                    color = colors.ink,
                    modifier = Modifier.cascade(wave, 0).padding(start = 4.dp).semantics { heading() },
                )
                Text(
                    stringResource(R.string.pro_lead, billing.price),
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
                if (billing.isTest) {
                    Text(
                        stringResource(R.string.pro_test_note),
                        style = type.caption.copy(fontSize = 12.5.sp),
                        color = colors.muted,
                        modifier = Modifier.cascade(wave, 3).padding(start = 4.dp, top = 12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp)) {
                val label = if (pro) stringResource(R.string.pro_active) else stringResource(R.string.pro_buy, billing.price)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (pro) colors.goodSoft else colors.accent)
                        .clickable(enabled = !pro && !buying, role = Role.Button) {
                            buying = true
                            scope.launch {
                                if (billing.purchase()) {
                                    haptics.play(Haptic.CONFIRM)
                                    onBought()
                                }
                                buying = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = type.bodyStrong.copy(fontSize = 16.sp), color = if (pro) colors.good else colors.onAccent)
                }
                if (pro && billing.isTest) {
                    Text(
                        stringResource(R.string.pro_reset_test),
                        style = type.bodyStrong,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) {
                                haptics.play(Haptic.TICK)
                                onReset()
                            }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}
