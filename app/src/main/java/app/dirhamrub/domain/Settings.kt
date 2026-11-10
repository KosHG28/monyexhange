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

/** Карты на калькуляторе: одной строкой (выбранная) или рядом (все карты карточками). */
enum class CardsView { LINE, ROW }

/** Готовый вид калькулятора: «Просто», «Подробно» или свой набор. */
enum class MainLayout { SIMPLE, DETAILED, CUSTOM }

/** Настройки приложения (экран «Настройки»). */
data class AppSettings(
    /** Курсы UnionPay: карты UnionPay и справочный курс валют, которых нет у ЦБ. */
    val unionPay: Boolean = true,
    val ratesUpdate: RatesUpdate = RatesUpdate.EVERY_6H,
    /** Подсказка «Вы в Армении — считать в драмах?» по стране сотовой сети. */
    val countryHint: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val motion: MotionMode = MotionMode.SYSTEM,
    /** Цвета из обоев (Material You, Android 12+). */
    val dynamicColor: Boolean = false,
    /** Вибрация при нажатии клавиш калькулятора. */
    val keyHaptics: Boolean = true,
    /** Что показывать на калькуляторе (настройки «Главный экран»). */
    val cardsView: CardsView = CardsView.LINE,
    val showCompare: Boolean = true,
    val showOfficial: Boolean = false,
    val showDebit: Boolean = true,
    val showChips: Boolean = false,
) {
    /** Какому готовому виду соответствует набор; иначе — «свой». */
    val mainLayout: MainLayout
        get() = when (this) {
            withLayout(MainLayout.SIMPLE) -> MainLayout.SIMPLE
            withLayout(MainLayout.DETAILED) -> MainLayout.DETAILED
            else -> MainLayout.CUSTOM
        }

    /** Готовый вид: «Просто» — карта строкой, без строки ЦБ и быстрых сумм; «Подробно» — всё, карты рядом. */
    fun withLayout(layout: MainLayout): AppSettings = when (layout) {
        MainLayout.SIMPLE -> copy(cardsView = CardsView.LINE, showCompare = true, showOfficial = false, showDebit = true, showChips = false)
        MainLayout.DETAILED -> copy(cardsView = CardsView.ROW, showCompare = true, showOfficial = true, showDebit = true, showChips = true)
        MainLayout.CUSTOM -> this
    }
}

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
}
