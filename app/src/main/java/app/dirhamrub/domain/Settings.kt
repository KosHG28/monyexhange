package app.dirhamrub.domain

/** Как часто скачивать курсы ЦБ и UnionPay. */
enum class RatesUpdate {
    /** При каждом запуске приложения. */
    LAUNCH,

    /** При запуске и при возвращении в приложение, если курсам больше 6 часов. */
    EVERY_6H,

    /** Только кнопкой «Обновить»: без неё приложение в сеть не ходит. */
    MANUAL,
}

/** Тема: как в системе или своя. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Анимации: как в системе («убрать анимацию») или выключены в приложении. */
enum class MotionMode { SYSTEM, OFF }

/** Значок приложения (Pro): классический, тёмный, золотой. */
enum class AppIcon { CLASSIC, DARK, GOLD }

/** Округление итога и сумм в карточках: без округления (с копейками), до целых, до десятков. */
enum class Rounding { NONE, WHOLE, TENS }

/** Настройки приложения (экран «Настройки»). */
data class AppSettings(
    /** Курсы UnionPay: карты UnionPay и справочный курс валют, которых нет у ЦБ. */
    val unionPay: Boolean = true,
    val ratesUpdate: RatesUpdate = RatesUpdate.EVERY_6H,
    /** Подсказка «Вы в Армении — считать в драмах?» по стране сотовой сети. */
    val countryHint: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Тёмная тема на чисто чёрном фоне (OLED: пиксели выключены, меньше расход батареи). */
    val pureBlack: Boolean = false,
    val motion: MotionMode = MotionMode.SYSTEM,
    /** Вибрация при нажатии клавиш калькулятора. */
    val keyHaptics: Boolean = true,
    /** Сами (без кнопки «Обновить») курсы скачиваются только по Wi-Fi и другим безлимитным сетям — роуминг дорогой. */
    val wifiOnly: Boolean = false,
    val rounding: Rounding = Rounding.WHOLE,
    /** Активирована Pro-версия (пока — по общему промокоду без оплаты). */
    val pro: Boolean = false,
    /** Pro: синий акцент — из цветов обоев (Android 12+). */
    val dynamicColor: Boolean = false,
    /** Pro: какой значок у приложения. */
    val appIcon: AppIcon = AppIcon.CLASSIC,
)

/** Когда скачивать курсы: по настройке [RatesUpdate], поводу и времени прошлой загрузки. */
object RatesPolicy {
    const val TTL_MS = 6 * 60 * 60 * 1000L

    /** Повод: запуск приложения, возвращение в него, кнопка «Обновить». */
    enum class Trigger { LAUNCH, RESUME, BUTTON }

    /** [fetchedAt] — когда курсы скачаны (мс), 0 — никогда или они от старой версии приложения. */
    fun due(update: RatesUpdate, trigger: Trigger, fetchedAt: Long, now: Long): Boolean = when {
        trigger == Trigger.BUTTON -> true
        update == RatesUpdate.MANUAL -> false
        update == RatesUpdate.LAUNCH -> trigger == Trigger.LAUNCH || fetchedAt <= 0L
        else -> fetchedAt <= 0L || now < fetchedAt || now - fetchedAt >= TTL_MS
    }

    /** Без кнопки ходить в сеть можно (курсы неделю назад, на даты чеков) — если обновление не ручное. */
    fun auto(update: RatesUpdate): Boolean = update != RatesUpdate.MANUAL

    /**
     * Можно ли сейчас в сеть: кнопкой — всегда; сами — только если не «только по Wi-Fi»
     * или сеть безлимитная ([unmetered]: Wi-Fi, Ethernet).
     */
    fun network(trigger: Trigger, wifiOnly: Boolean, unmetered: Boolean): Boolean =
        trigger == Trigger.BUTTON || !wifiOnly || unmetered
}
