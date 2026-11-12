package app.dirhamrub.ui.theme

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/**
 * Наклон телефона влево-вправо, −1…1 (±20° от «ноля»), для блика на картах. null — блика нет: Android ниже 13,
 * анимации выключены (или энергосбережение — [LocalReduceMotion]), нет гироскопа.
 */
val LocalTilt = staticCompositionLocalOf<State<Float>?> { null }

/** Датчик наклона на всё приложение: слушаем, только пока оно на экране, ~30 раз в секунду. */
@Composable
fun ProvideTilt(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTilt provides rememberTilt(), content = content)
}

@Composable
private fun rememberTilt(): State<Float>? {
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember { context.getSystemService(SensorManager::class.java) }
    // Игровой вектор поворота: гироскоп + акселерометр, без компаса — не «плывёт» у металла.
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) }
    val enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reduceMotion && manager != null && sensor != null
    val tilt = remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled, lifecycle) {
        if (!enabled || manager == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = TiltListener { tilt.floatValue = it }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> manager.registerListener(listener, sensor, SAMPLING_US)
                Lifecycle.Event.ON_PAUSE -> {
                    manager.unregisterListener(listener)
                    listener.reset()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    return if (enabled) tilt else null
}

/**
 * Наклон из вектора поворота: составляющая «вверх» по оси X телефона — работает при любом угле, под которым
 * держат телефон. «Ноль» — как держали при открытии; потом плавно (≈2 с) подтягивается к новому положению руки.
 * Значение сглажено — полоса двигается с инерцией, без дрожания.
 */
private class TiltListener(private val onTilt: (Float) -> Unit) : SensorEventListener {
    private val matrix = FloatArray(9)
    private var base = Float.NaN
    private var smooth = 0f
    private var last = 0L

    fun reset() {
        base = Float.NaN
        last = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val up = -matrix[6]
        val dt = if (last == 0L) 0f else ((event.timestamp - last) / 1e9f).coerceIn(0f, 0.2f)
        last = event.timestamp
        if (base.isNaN()) base = up
        base += (up - base) * (dt / BASELINE_S).coerceAtMost(1f)
        val target = ((up - base) / RANGE).coerceIn(-1f, 1f)
        smooth += (target - smooth) * SMOOTHING
        if (abs(smooth) < 0.001f) smooth = 0f
        onTilt(smooth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Блик на карте: мягкая диагональная полоса света. Свет один на весь экран — полоса проходит через точку на
 * середине высоты окна, её место по ширине задаёт наклон; поэтому на соседних картах блик в разных местах.
 * Ровно — почти не видна, наклон — ярче; в тёмной теме — 60 % от светлой. Без [LocalTilt] — ничего.
 * [halfWidth] — полуширина полосы (у мини-карты узкая). Ставить после clip и фона карты.
 */
fun Modifier.sheen(halfWidth: Dp = 44.dp): Modifier = composed {
    val tilt = LocalTilt.current ?: return@composed this
    val dark = AppTheme.colors.isDark
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }
    val half = with(density) { halfWidth.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithContent {
            drawContent()
            val t = tilt.value
            // Центр полосы в координатах окна → в координатах карты.
            val center = Offset(screenW * (0.5f + 0.55f * t), screenH / 2f) - origin
            val strength = (REST + (PEAK - REST) * abs(t)) * if (dark) DARK else 1f
            val start = center - DIRECTION * half
            val end = center + DIRECTION * half
            drawRect(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent), start, end),
                blendMode = BlendMode.SoftLight,
            )
            // Яркая сердцевина — только когда блик «поймал свет».
            if (strength > 0.4f) {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = strength * 0.3f), Color.Transparent),
                        center - DIRECTION * (half * 0.45f),
                        center + DIRECTION * (half * 0.45f),
                    ),
                    blendMode = BlendMode.Screen,
                )
            }
        }
}

/** Водяной знак «глубже» в пластике: отстаёт от наклона на [shift]. Для graphicsLayer: translationX. */
fun parallaxOf(tilt: State<Float>?, shiftPx: Float): Float = -(tilt?.value ?: 0f) * shiftPx

/** Направление поперёк полосы (полоса — под углом, как свет из окна сверху-слева). */
private val DIRECTION = Offset(0.906f, 0.423f)
private const val REST = 0.28f
private const val PEAK = 0.6f
private const val DARK = 0.6f
private const val RANGE = 0.34f
private const val BASELINE_S = 2f
private const val SMOOTHING = 0.25f
private const val SAMPLING_US = 33_000
