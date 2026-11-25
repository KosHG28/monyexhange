package app.dirhamrub.ui.theme

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
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

internal val LocalCardTilt = staticCompositionLocalOf<State<Offset>?> { null }

/** Один датчик на главное окно, в том числе для прежних одноосевых эффектов. */
@Composable
fun ProvideTilt(content: @Composable () -> Unit) {
    val tilt = rememberReflectionTilt(active = true)
    val legacy: State<Float>? = if (LocalReduceMotion.current) null else remember(tilt) {
        derivedStateOf { tilt.value.x }
    }
    CompositionLocalProvider(LocalCardTilt provides tilt, LocalTilt provides legacy, content = content)
}

@Composable
internal fun rememberTilt(active: Boolean = true): State<Float>? {
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LocalReduceMotion.current
    val tilt = remember { mutableFloatStateOf(0f) }
    val filter = remember { TiltFilter { tilt.floatValue = it } }
    val available = ObserveRotation(active && supported, onStart = filter::reset) { matrix, timestamp, _ -> filter.read(matrix, timestamp) }
    return tilt.takeIf { supported && available }
}

@Composable
internal fun ObserveRotation(
    active: Boolean,
    onStart: () -> Unit,
    onAcceleration: ((FloatArray, Long, Int) -> Unit)? = null,
    trackWhenUnfocused: Boolean = false,
    onReading: (matrix: FloatArray, timestamp: Long, rotation: Int) -> Unit,
): Boolean {
    val focused = LocalWindowInfo.current.isWindowFocused
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    // Предпочитаем вектор без компаса. На некоторых устройствах доступен только обычный вектор поворота.
    val useAcceleration = onAcceleration != null
    val sensors = remember(manager, useAcceleration) {
        listOfNotNull(
            manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
            manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            if (useAcceleration) manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null,
        )
    }
    val enabled = active && (focused || trackWhenUnfocused) && !reduceMotion
    val start by rememberUpdatedState(onStart)
    val reading by rememberUpdatedState(onReading)
    val acceleration by rememberUpdatedState(onAcceleration)
    DisposableEffect(enabled, manager, sensors, lifecycle, view) {
        if (!enabled || manager == null || sensors.isEmpty()) return@DisposableEffect onDispose { }
        val matrix = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotation = view.display?.rotation ?: 0
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    acceleration?.invoke(event.values, event.timestamp, rotation)
                } else {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    reading(matrix, event.timestamp, rotation)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    start()
                    sensors.any { manager.registerListener(listener, it, SAMPLING_US) }
                }
                Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    return sensors.isNotEmpty()
}

/**
 * Наклон из вектора поворота: составляющая «вверх» по оси X телефона — работает при любом угле, под которым
 * держат телефон. «Ноль» — как держали при открытии; потом плавно (≈2 с) подтягивается к новому положению руки.
 * Значение сглажено — полоса двигается с инерцией, без дрожания.
 */
private class TiltFilter(private val onTilt: (Float) -> Unit) {
    private val publication = TiltPublicationGate()
    private var base = Float.NaN
    private var smooth = 0f
    private var last = 0L

    fun reset() {
        base = Float.NaN
        last = 0L
        smooth = 0f
        publication.reset()
        onTilt(0f)
    }

    fun read(matrix: FloatArray, timestamp: Long) {
        val up = -matrix[6]
        val dt = if (last == 0L) 0f else ((timestamp - last) / 1e9f).coerceIn(0f, 0.2f)
        last = timestamp
        if (base.isNaN()) base = up
        base += (up - base) * (dt / BASELINE_S).coerceAtMost(1f)
        val target = ((up - base) / RANGE).coerceIn(-1f, 1f)
        smooth += (target - smooth) * SMOOTHING
        if (abs(smooth) < 0.001f) smooth = 0f
        if (publication.accept(smooth)) onTilt(smooth)
    }

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
                blendMode = BlendMode.Softlight,
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
