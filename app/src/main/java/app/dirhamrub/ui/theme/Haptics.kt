package app.dirhamrub.ui.theme

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** События с виброоткликом: у каждого свой характер. */
enum class Haptic {
    /** Цифра на клавиатуре: тонкий низкий щелчок. */
    KEY,

    /** «Стереть», «+ − ×», мелкие кнопки: обычный щелчок. */
    TICK,

    /** Долгое «стереть» (очистить всё), удаление карты: мягкий «бум». */
    THUD,

    /** Выбрали способ оплаты на главном. */
    SELECT,

    /** Сменилась самая выгодная карта: короткое нарастание и щелчок. */
    BEST,

    /** Камера распознала цену: еле заметный щелчок (не чаще раза в 0,3 с). */
    CAMERA,

    /** Готово: скриншот распознан, карта сохранена, «Начать». Двойной щелчок. */
    CONFIRM,

    /** Не вышло: на скриншоте сумм нет. */
    REJECT,

    /** Переключатель включили / выключили. */
    TOGGLE_ON,
    TOGGLE_OFF,

    /** Шаг знакомства сменился. */
    STEP,

    /** Список упёрся в край прокрутки: мягкий щелчок, один раз на упор. */
    EDGE,
}

/**
 * Виброотклик в три уровня. Флагманы (Android 12+, вибромотор умеет «примитивы» — Pixel 6+, Galaxy S21+ и
 * подобные): у каждого события своя композиция примитивов своей силы. Остальные Android 11+ с управлением силой
 * вибрации — системные отклики ([HapticFeedbackConstants]). Старые и дешёвые моторы — только клавиши и
 * «очистить», чтобы не было грубого дребезжания. Выключено в системе («Вибрация при касании») или в настройке
 * «Виброотклик» — тишина.
 */
class Haptics internal constructor(private val view: View, private val enabled: Boolean) {
    private val vibrator: Vibrator? = vibratorOf(view.context)
    private val rich: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null && richSupported(vibrator)
    private val modern: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.hasAmplitudeControl() == true
    private var lastCamera = 0L

    fun play(event: Haptic) {
        if (!enabled || !systemTouchHaptics()) return
        // Клавиатуре нужен только LOW_TICK: отсутствие других примитивов не лишает её мягкого отклика.
        if (event == Haptic.KEY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) == true
        ) {
            playRich(event)
            return
        }
        if (event == Haptic.CAMERA) {
            val now = SystemClock.uptimeMillis()
            if (now - lastCamera < CAMERA_GAP_MS) return
            lastCamera = now
        }
        if (rich && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            playRich(event)
            return
        }
        val constant = fallback(event) ?: return
        view.performHapticFeedback(constant)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun playRich(event: Haptic) {
        val c = VibrationEffect.startComposition()
        when (event) {
            Haptic.KEY -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.25f)
            Haptic.TICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
            Haptic.THUD -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f)
            Haptic.SELECT -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
            Haptic.BEST -> {
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.35f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 30)
            }
            Haptic.CAMERA -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.3f)
            Haptic.CONFIRM -> {
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 70)
            }
            Haptic.REJECT -> {
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f, 60)
            }
            Haptic.TOGGLE_ON -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f)
            Haptic.TOGGLE_OFF -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f)
            Haptic.STEP -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
            Haptic.EDGE -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.5f)
        }
        val effect = c.compose()
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // «Касание»: громкость берётся из системной настройки вибрации при касании.
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect)
        }
    }

    /** Системный отклик для обычных телефонов; null — без отклика (старый мотор или нет подходящего). */
    private fun fallback(event: Haptic): Int? {
        val sdk = Build.VERSION.SDK_INT
        // Старые моторы: только клавиши и «очистить».
        if (sdk < Build.VERSION_CODES.R || !modern) {
            return when (event) {
                Haptic.KEY, Haptic.TICK -> HapticFeedbackConstants.KEYBOARD_TAP
                Haptic.THUD -> HapticFeedbackConstants.LONG_PRESS
                else -> null
            }
        }
        val u = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return when (event) {
            Haptic.KEY -> if (sdk >= u) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.TICK -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.THUD -> HapticFeedbackConstants.LONG_PRESS
            Haptic.SELECT -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.BEST, Haptic.CONFIRM -> HapticFeedbackConstants.CONFIRM
            Haptic.REJECT -> HapticFeedbackConstants.REJECT
            Haptic.CAMERA -> if (sdk >= u) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else null
            Haptic.TOGGLE_ON -> if (sdk >= u) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
            Haptic.TOGGLE_OFF -> if (sdk >= u) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
            Haptic.STEP -> if (sdk >= u) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            Haptic.EDGE -> if (sdk >= u) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else null
        }
    }

    /**
     * Системная «Вибрация при касании»: до Android 13 прямой вибромотор её сам не учитывает — проверяем;
     * с Android 13 это делает сама система по [VibrationAttributes.USAGE_TOUCH].
     */
    @Suppress("DEPRECATION")
    private fun systemTouchHaptics(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            runCatching { Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0 }
                .getOrDefault(true)

    private companion object {
        const val CAMERA_GAP_MS = 300L

        fun vibratorOf(context: Context): Vibrator? {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            return v?.takeIf { it.hasVibrator() }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        fun richSupported(v: Vibrator): Boolean = v.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
        )
    }
}

/** Виброотклик экрана: учитывает настройку «Виброотклик» ([LocalKeyHaptics]). */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalKeyHaptics.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
