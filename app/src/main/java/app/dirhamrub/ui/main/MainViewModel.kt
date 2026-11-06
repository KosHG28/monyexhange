package app.dirhamrub.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dirhamrub.data.CbrClient
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.data.settingsDataStore
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.ReceiptAge
import app.dirhamrub.domain.wallet.StaleReceipt
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletQuote
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Одна сумма в трёх валютах: трат, итоговой и выбранной карты (null — курса нет). */
data class Totals(val spend: BigDecimal, val home: BigDecimal?, val card: BigDecimal?) {
    fun of(line: Line): BigDecimal? = when (line) {
        Line.SPEND -> spend
        Line.HOME -> home
        Line.CARD -> card
    }
}

data class MainUiState(
    /** Сохранённое состояние уже прочитано из DataStore. */
    val loaded: Boolean = false,
    val wallet: Wallet = Wallet(),
    val official: OfficialRates? = null,
    /** Курсы ЦБ неделю назад — для стрелки «за неделю». */
    val officialWeekAgo: OfficialRates? = null,
    val ratesLoading: Boolean = false,
    val ratesFailed: Boolean = false,
    val active: Line = Line.SPEND,
    val expression: Expression = Expression(),
    /** Страна сети, для которой подсказку о валюте уже приняли или закрыли. */
    val countryHandled: String? = null,
    val onboardingDone: Boolean = true,
    /** Камера вписывает суммы в ценники (иначе — плашки над ценами). */
    val cameraEmbedded: Boolean = true,
    /** Курсы ЦБ на даты чеков. */
    val ratesHistory: Map<LocalDate, OfficialRates> = emptyMap(),
    val cameraUsed: Boolean = false,
    val tileOffered: Boolean = true,
) {
    /** Устаревший чек выбранной карты: давно, а ЦБ с тех пор заметно сдвинулся. */
    val staleReceipt: StaleReceipt?
        get() = selectedCard?.let { ReceiptAge.check(it, wallet.spend, wallet.home, LocalDate.now(), official, ratesHistory) }

    /** Изменение курса ЦБ валюты трат к итоговой за неделю, в процентах. */
    val weekTrend: BigDecimal? get() = official?.trendPercent(officialWeekAgo, wallet.spend, wallet.home)

    val quote: WalletQuote by lazy { RateEngine.quoteAll(wallet, official) }
    val selectedCard: BankCard? get() = wallet.selected
    val selectedQuote: CardQuote? get() = quote.of(selectedCard?.id)

    /** Курс для расчёта: выбранная карта, а без карт — курс ЦБ. */
    val homePerUnit: BigDecimal? get() = selectedQuote?.homePerUnit ?: quote.official
    val cardPerUnit: BigDecimal? get() = selectedQuote?.cardPerUnit

    /** Строка валюты карты нужна, только если она отличается от валют трат и итога. */
    val showCardLine: Boolean
        get() = selectedQuote != null && selectedCard!!.currency != wallet.spend && selectedCard!!.currency != wallet.home

    val totals: Totals by lazy {
        val v = expression.value
        val hp = homePerUnit
        val cp = cardPerUnit
        val spend = when (active) {
            Line.SPEND -> v
            Line.HOME -> hp?.let { divideOrZero(v, it) } ?: BigDecimal.ZERO
            Line.CARD -> cp?.let { divideOrZero(v, it) } ?: BigDecimal.ZERO
        }
        Totals(
            spend = spend,
            home = if (active == Line.HOME) v else hp?.let { spend.multiply(it, MC) },
            card = if (active == Line.CARD) v else cp?.let { spend.multiply(it, MC) },
        )
    }

    /** Текст активной строки: набираемое число или итог выражения «22 + 15,5». */
    val activeText: String
        get() = when {
            expression.isEmpty -> "0"
            expression.isCompound -> Numbers.money(expression.value)
            else -> expression.currentTerm()
        }
}

class MainViewModel(private val repository: WalletRepository) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var officialFetchedAt = 0L

    init {
        viewModelScope.launch {
            val stored = repository.load()
            officialFetchedAt = stored.officialFetchedAt
            _state.value = MainUiState(
                loaded = true,
                wallet = stored.wallet,
                official = stored.official,
                officialWeekAgo = stored.officialWeekAgo,
                active = if (stored.active == Line.CARD && stored.wallet.cards.isEmpty()) Line.SPEND else stored.active,
                expression = stored.expression,
                countryHandled = stored.countryHandled,
                // Знакомство — только для нового пользователя: у перешедших со старых версий карты уже есть.
                onboardingDone = stored.onboardingDone || stored.wallet.cards.isNotEmpty(),
                cameraEmbedded = stored.cameraEmbedded,
                ratesHistory = stored.ratesHistory,
                cameraUsed = stored.cameraUsed,
                tileOffered = stored.tileOffered,
            )
            refreshRates(force = false)
            // Сохраняем не чаще раза в 300 мс: collectLatest отменяет отложенную запись при новом нажатии.
            _state.collectLatest { s ->
                delay(SAVE_DELAY_MS)
                repository.saveSession(s.wallet, s.active, s.expression)
            }
        }
    }

    fun press(key: Key) = _state.update { it.copy(expression = it.expression.press(key)) }

    fun setActive(line: Line) = _state.update { s ->
        if (s.active == line) s else s.copy(active = line, expression = Expression.of(s.totals.of(line) ?: BigDecimal.ZERO))
    }

    fun selectCard(id: String) = _state.update { s ->
        when {
            s.wallet.selectedId == id -> s
            // Сумма в валюте карты не должна прыгнуть: сначала переводим её в валюту трат.
            s.active == Line.CARD -> s.copy(
                wallet = s.wallet.copy(selectedId = id),
                active = Line.SPEND,
                expression = Expression.of(s.totals.spend),
            )
            else -> s.copy(wallet = s.wallet.copy(selectedId = id))
        }
    }

    /** Другая валюта трат: сохраняем сумму в итоговой валюте и пересчитываем в новую валюту. */
    fun setSpend(currency: Currency) {
        _state.update { s ->
            if (s.wallet.spend == currency) return@update s
            val home = s.totals.home
            val next = s.copy(wallet = s.wallet.copy(spend = currency), active = Line.SPEND)
            val rate = next.homePerUnit
            val amount = if (home != null && rate != null) divideOrZero(home, rate) else BigDecimal.ZERO
            next.copy(expression = Expression.of(amount))
        }
        refreshReceiptRates()
    }

    /** Другая итоговая валюта: сумма в валюте трат остаётся. */
    fun setHome(currency: Currency) {
        _state.update { s ->
            if (s.wallet.home == currency) s
            else s.copy(wallet = s.wallet.copy(home = currency), active = Line.SPEND, expression = Expression.of(s.totals.spend))
        }
        refreshReceiptRates()
    }

    /** Быстрая сумма в валюте трат: после «+» подставляется в выражение, иначе заменяет его. */
    fun putAmount(amount: BigDecimal) = _state.update { s ->
        if (s.active == Line.SPEND) s.copy(expression = s.expression.putAmount(amount))
        else s.copy(active = Line.SPEND, expression = Expression.of(amount))
    }

    /** Цены из камеры: одна — заменяет сумму, несколько — становятся выражением «a + b + c». */
    fun putItems(items: List<BigDecimal>) = _state.update { s ->
        val expression = when (items.size) {
            0 -> s.expression
            1 -> Expression.of(items.single())
            else -> Expression(items.map { Numbers.plain(it) })
        }
        s.copy(active = Line.SPEND, expression = expression)
    }

    /** Подсказка «вы в другой стране»: [accept] — переключить валюту трат; больше для этой страны не показываем. */
    fun handleCountry(iso: String, accept: Currency?) {
        if (accept != null) setSpend(accept)
        _state.update { it.copy(countryHandled = iso) }
        viewModelScope.launch { repository.saveCountryHandled(iso) }
    }

    /** Соседняя карта (свайп по сумме): [step] = +1 — следующая, −1 — предыдущая; у края ряда ничего не делает. */
    fun stepCard(step: Int) {
        val w = _state.value.wallet
        val i = w.cards.indexOfFirst { it.id == w.selected?.id }
        val next = w.cards.getOrNull(i + step) ?: return
        selectCard(next.id)
    }

    fun saveCard(card: BankCard) {
        _state.update { s -> s.copy(wallet = s.wallet.upsert(card).copy(selectedId = card.id)) }
        refreshReceiptRates()
    }

    fun deleteCard(id: String) = _state.update { s ->
        val wallet = s.wallet.remove(id)
        s.copy(wallet = wallet, active = if (wallet.cards.isEmpty() && s.active == Line.CARD) Line.SPEND else s.active)
    }

    /** Пустая карта для редактора: валюта трат по умолчанию — первая, которой ещё нет среди карт. */
    fun newCard(): BankCard {
        val w = _state.value.wallet
        val currency = listOf(w.spend, w.home).firstOrNull { c -> w.cards.none { it.currency == c } } ?: w.spend
        return BankCard(id = UUID.randomUUID().toString(), name = "", currency = currency, style = w.nextStyle())
    }

    /** Курсы ЦБ: при запуске — если старше 6 часов, по кнопке — всегда. */
    fun refreshRates(force: Boolean = true) {
        val s = _state.value
        if (s.ratesLoading) return
        if (!force && s.official != null && System.currentTimeMillis() - officialFetchedAt < RATES_TTL_MS) {
            refreshWeekAgo()
            refreshReceiptRates()
            return
        }
        _state.update { it.copy(ratesLoading = true, ratesFailed = false) }
        viewModelScope.launch {
            val rates = CbrClient.fetch()
            if (rates != null) {
                officialFetchedAt = System.currentTimeMillis()
                repository.saveOfficial(rates, officialFetchedAt)
            }
            _state.update { it.copy(official = rates ?: it.official, ratesLoading = false, ratesFailed = rates == null) }
            refreshWeekAgo()
            refreshReceiptRates()
        }
    }

    private val historyLoading = HashSet<LocalDate>()

    /** Курсы ЦБ на даты старых чеков (для «чек устарел»): качаем недостающие, не больше нескольких за раз. */
    private fun refreshReceiptRates() {
        val s = _state.value
        if (s.official == null) return
        val today = LocalDate.now()
        val missing = s.wallet.cards
            .flatMap { ReceiptAge.datesToCheck(it, s.wallet.spend, s.wallet.home, today) }
            .distinct()
            .filter { it !in s.ratesHistory && it !in historyLoading }
            .take(HISTORY_BATCH)
        if (missing.isEmpty()) return
        historyLoading += missing
        viewModelScope.launch {
            for (day in missing) {
                val rates = CbrClient.fetchOn(day) ?: continue
                _state.update { st -> st.copy(ratesHistory = (st.ratesHistory + (day to rates)).entries.sortedByDescending { it.key }.take(HISTORY_MAX).associate { it.key to it.value }) }
            }
            historyLoading -= missing.toSet()
            repository.saveRatesHistory(_state.value.ratesHistory)
        }
    }

    private var weekLoading = false

    /** Курсы неделю назад для стрелки тренда: качаем, только если их нет или они не за ту неделю. */
    private fun refreshWeekAgo() {
        val s = _state.value
        val official = s.official ?: return
        val target = official.date.minusDays(WEEK_DAYS)
        val have = s.officialWeekAgo?.date
        val fits = have != null && !have.isBefore(target.minusDays(WEEK_SLACK_DAYS)) && !have.isAfter(target.plusDays(WEEK_SLACK_DAYS))
        if (weekLoading || fits) return
        weekLoading = true
        viewModelScope.launch {
            val rates = CbrClient.fetchOn(target)
            if (rates != null) {
                repository.saveOfficialWeekAgo(rates)
                _state.update { it.copy(officialWeekAgo = rates) }
            }
            weekLoading = false
        }
    }

    /** Камеру открыли — после неё можно предложить плитку «Ценник» в шторке. */
    fun markCameraUsed() {
        if (_state.value.cameraUsed) return
        _state.update { it.copy(cameraUsed = true) }
        viewModelScope.launch { repository.saveCameraUsed() }
    }

    /** Плитку предложили (добавили или отказались) — больше не спрашиваем. */
    fun markTileOffered() {
        _state.update { it.copy(tileOffered = true) }
        viewModelScope.launch { repository.saveTileOffered() }
    }

    fun setCameraEmbedded(value: Boolean) {
        _state.update { it.copy(cameraEmbedded = value) }
        viewModelScope.launch { repository.saveCameraEmbedded(value) }
    }

    /** Знакомство пройдено: больше не показываем. */
    fun finishOnboarding() {
        _state.update { it.copy(onboardingDone = true) }
        viewModelScope.launch { repository.saveOnboardingDone() }
    }

    companion object {
        private const val SAVE_DELAY_MS = 300L
        private const val RATES_TTL_MS = 6 * 60 * 60 * 1000L
        private const val WEEK_DAYS = 7L
        private const val WEEK_SLACK_DAYS = 3L
        private const val HISTORY_BATCH = 5
        private const val HISTORY_MAX = 30

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                MainViewModel(WalletRepository(app.settingsDataStore))
            }
        }

        /** Быстрые суммы в валюте трат; у дирхама 22 — проезд в метро. */
        fun chips(c: Currency): List<BigDecimal> = when (c) {
            Currency.AED -> listOf(5, 10, 22, 50, 100, 200, 500)
            Currency.TRY, Currency.THB -> listOf(50, 100, 200, 500, 1000, 2000, 5000)
            Currency.CNY -> listOf(10, 20, 50, 100, 200, 500, 1000)
            Currency.KZT, Currency.AMD -> listOf(500, 1000, 2000, 5000, 10000, 20000, 50000)
            Currency.VND -> listOf(10000, 20000, 50000, 100000, 200000, 500000, 1000000)
            else -> listOf(1, 5, 10, 20, 50, 100, 200)
        }.map { BigDecimal(it) }

        /** Строки шпаргалки: девять сумм, от мелкой покупки до крупной. */
        fun cheat(c: Currency): List<BigDecimal> = when (c) {
            Currency.AED, Currency.CNY -> listOf(1, 5, 10, 20, 50, 100, 200, 500, 1000)
            Currency.TRY, Currency.THB -> listOf(10, 50, 100, 200, 500, 1000, 2000, 5000, 10000)
            Currency.KZT, Currency.AMD -> listOf(100, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000)
            Currency.VND -> listOf(1000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1000000)
            else -> listOf(1, 2, 5, 10, 20, 50, 100, 200, 500)
        }.map { BigDecimal(it) }
    }
}

internal fun divideOrZero(value: BigDecimal, divisor: BigDecimal): BigDecimal =
    if (divisor.signum() == 0) BigDecimal.ZERO else value.divide(divisor, MC)
