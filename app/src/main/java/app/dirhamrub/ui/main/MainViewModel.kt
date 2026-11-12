package app.dirhamrub.ui.main

import app.dirhamrub.domain.AppIcon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dirhamrub.data.CbrClient
import app.dirhamrub.data.GuideClient
import app.dirhamrub.data.NetworkState
import app.dirhamrub.data.UnionPayClient
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.data.ratesDataStore
import app.dirhamrub.data.settingsDataStore
import app.dirhamrub.data.unionPayDataStore
import app.dirhamrub.domain.AppSettings
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.MotionMode
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.RatesPolicy
import app.dirhamrub.domain.RatesPolicy.Trigger
import app.dirhamrub.domain.RatesUpdate
import app.dirhamrub.domain.Rounding
import app.dirhamrub.domain.ThemeMode
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.CountryGuideParser
import app.dirhamrub.domain.wallet.CountryGuides
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.ReceiptAge
import app.dirhamrub.domain.wallet.StaleReceipt
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletQuote
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** Курсы UnionPay: карты UnionPay и справочный курс валют, которых нет у ЦБ. */
    val unionPay: UnionPayRates? = null,
    val settings: AppSettings = AppSettings(),
    /** Когда скачаны курсы ЦБ (мс, 0 — никогда) и откуда — для настроек. */
    val officialFetchedAt: Long = 0L,
    val officialSource: String? = null,
    val unionPayFetchedAt: Long = 0L,
    /** Справочник «Чем платить в стране»: встроенный или скачанный, что свежее. */
    val guides: CountryGuides? = null,
) {
    /** Курсы UnionPay, если они включены в настройках. */
    val activeUnionPay: UnionPayRates? get() = unionPay.takeIf { settings.unionPay }

    /** Справочные курсы: ЦБ, а валюты без ЦБ — по UnionPay к доллару и ЦБ доллара (они в [OfficialRates.derived]). */
    val reference: OfficialRates? by lazy { QuoteCache.reference(official, activeUnionPay) }

    /** Устаревший чек выбранной карты: давно, а ЦБ с тех пор заметно сдвинулся. */
    val staleReceipt: StaleReceipt?
        get() = selectedCard?.let { ReceiptAge.check(it, wallet.spend, wallet.home, LocalDate.now(), official, ratesHistory) }

    /** Изменение курса ЦБ валюты трат к итоговой за неделю, в процентах. */
    val weekTrend: BigDecimal? get() = official?.trendPercent(officialWeekAgo, wallet.spend, wallet.home)

    val quote: WalletQuote by lazy { QuoteCache.quote(wallet, reference, activeUnionPay) }
    /** Чем считаем: выбранный вручную способ, а без ручного выбора — самый выгодный ([Wallet.pick]). */
    val selectedCard: BankCard? get() = wallet.pick(quote.best?.card?.id)
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

/**
 * Последний расчёт справочных курсов и курсов карт. Они зависят только от кошелька и курсов, а состояние
 * пересоздаётся на каждое нажатие клавиши — без памяти ~150 валют и все карты считались бы заново на каждую цифру.
 * Сравнение — по ссылке (те же объекты), записи неизменяемые: гонка потоков даёт лишний расчёт, а не ошибку.
 */
private object QuoteCache {
    private class Ref(val official: OfficialRates?, val unionPay: UnionPayRates?, val result: OfficialRates?)
    private class Quote(val wallet: Wallet, val reference: OfficialRates?, val unionPay: UnionPayRates?, val result: WalletQuote)

    @Volatile private var ref: Ref? = null

    @Volatile private var quote: Quote? = null

    fun reference(official: OfficialRates?, unionPay: UnionPayRates?): OfficialRates? {
        ref?.let { if (it.official === official && it.unionPay === unionPay) return it.result }
        return official?.withUnionPay(unionPay).also { ref = Ref(official, unionPay, it) }
    }

    fun quote(wallet: Wallet, reference: OfficialRates?, unionPay: UnionPayRates?): WalletQuote {
        quote?.let { if (it.wallet === wallet && it.reference === reference && it.unionPay === unionPay) return it.result }
        return RateEngine.quoteAll(wallet, reference, unionPay).also { quote = Quote(wallet, reference, unionPay, it) }
    }
}

class MainViewModel(
    private val repository: WalletRepository,
    /** Встроенный справочник стран (`assets/countries.txt`). */
    private val bundledGuide: () -> String? = { null },
    /** Сеть сейчас безлимитная (Wi-Fi, Ethernet) — для «только по Wi-Fi». */
    private val unmetered: () -> Boolean = { true },
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var unionPayLoading = false

    init {
        viewModelScope.launch {
            val stored = repository.load()
            _state.value = MainUiState(
                loaded = true,
                // С переделки «конвертер» выбор по умолчанию — лучший способ: старый «выбранный» сбрасываем один раз.
                wallet = if (stored.selectionAuto) stored.wallet else stored.wallet.copy(selectedId = null),
                official = stored.official,
                officialWeekAgo = stored.officialWeekAgo,
                // Ввод — только в валюте трат или в итоговой (поля «цена» и «стоит вам»); валюты карты больше нет.
                active = if (stored.active == Line.CARD) Line.SPEND else stored.active,
                expression = stored.expression,
                countryHandled = stored.countryHandled,
                // Знакомство — только для нового пользователя: у перешедших со старых версий карты уже есть.
                onboardingDone = stored.onboardingDone || stored.wallet.cards.isNotEmpty(),
                cameraEmbedded = stored.cameraEmbedded,
                ratesHistory = stored.ratesHistory,
                cameraUsed = stored.cameraUsed,
                tileOffered = stored.tileOffered,
                unionPay = stored.unionPay,
                settings = stored.settings,
                officialFetchedAt = stored.officialFetchedAt,
                officialSource = stored.officialSource,
                unionPayFetchedAt = stored.unionPayFetchedAt,
                guides = loadGuides(stored.guideText),
            )
            if (!stored.selectionAuto) repository.markSelectionAuto(_state.value.wallet)
            repository.dropLegacy()
            refresh(Trigger.LAUNCH)
            // Карты и чеки пишем сразу: сохранил карту и тут же закрыл приложение — она не пропадёт.
            // Изменения, не касающиеся кошелька (курсы загружаются, настройки), запись не вызывают.
            launch {
                _state.map { it.wallet }.distinctUntilChanged().drop(1).collect { repository.saveWallet(it) }
            }
            // Набираемую сумму — не чаще раза в 300 мс: collectLatest отменяет отложенную запись при новом нажатии.
            _state.map { it.active to it.expression }.distinctUntilChanged().drop(1).collectLatest { (active, expression) ->
                delay(SAVE_DELAY_MS)
                repository.saveInput(active, expression)
            }
        }
    }

    /** Приложение ушло с экрана: дописываем то, что ждало своей задержки (последние нажатия). */
    fun flush() {
        val s = _state.value
        if (!s.loaded) return
        // Запись не отменяется, даже если экран закрывают в этот момент.
        viewModelScope.launch { withContext(NonCancellable) { repository.saveSession(s.wallet, s.active, s.expression) } }
    }

    fun press(key: Key) = _state.update { it.copy(expression = it.expression.press(key)) }

    /** Коснулись поля «цена» ([Line.SPEND]) или «стоит вам» ([Line.HOME]): вводим в нём, сумма не меняется. */
    fun setActive(line: Line) = _state.update { s ->
        if (s.active == line || line == Line.CARD) s
        else s.copy(active = line, expression = Expression.of(s.totals.of(line) ?: BigDecimal.ZERO))
    }

    /** Коснулись плитки способа: выбрать его; лучший — снова автовыбор ([Wallet.choose]). */
    fun selectCard(id: String) = _state.update { s ->
        val next = s.wallet.choose(id, s.quote.best?.card?.id)
        if (next == s.wallet) s else s.copy(wallet = next)
    }

    /**
     * Другая валюта трат: сохраняем сумму в итоговой валюте и пересчитываем в новую валюту. [place] — страна,
     * если её выбрали в списке стран (та же валюта, другая страна — меняется только шапка).
     */
    fun setSpend(currency: Currency, place: String? = null) {
        _state.update { s ->
            if (s.wallet.spend == currency) return@update s.copy(wallet = s.wallet.withSpend(currency, place))
            val home = s.totals.home
            val next = s.copy(wallet = s.wallet.withSpend(currency, place), active = Line.SPEND)
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
        if (accept != null) setSpend(accept, iso)
        _state.update { it.copy(countryHandled = iso) }
        viewModelScope.launch { repository.saveCountryHandled(iso) }
    }

    fun saveCard(card: BankCard) {
        _state.update { s -> s.copy(wallet = s.wallet.upsert(card)) }
        refreshReceiptRates()
    }

    fun deleteCard(id: String) = _state.update { s ->
        val wallet = s.wallet.remove(id)
        s.copy(wallet = wallet)
    }

    /** Пустая карта для редактора: валюта трат по умолчанию — первая, которой ещё нет среди карт. */
    fun newCard(): BankCard {
        val w = _state.value.wallet
        val currency = listOf(w.spend, w.home).firstOrNull { c -> w.cards.none { it.currency == c } } ?: w.spend
        return BankCard(id = UUID.randomUUID().toString(), name = "", currency = currency, style = w.nextStyle())
    }

    /** Кнопка «Обновить» (строка ЦБ, настройки): курсы ЦБ и UnionPay скачиваются всегда. */
    fun refreshRates() = refresh(Trigger.BUTTON)

    /** Вернулись в приложение: курсы старше 6 часов — скачиваем, если так в настройках. */
    fun onResume() {
        if (_state.value.loaded) refresh(Trigger.RESUME)
    }

    /** Курсы ЦБ и UnionPay — по настройке «Обновлять курсы» ([RatesPolicy]). */
    private fun refresh(trigger: Trigger) {
        val s = _state.value
        // «Только по Wi-Fi»: по мобильной сети сами не ходим, кнопкой — всегда.
        if (!RatesPolicy.network(trigger, s.settings.wifiOnly, unmetered())) return
        val now = System.currentTimeMillis()
        val update = s.settings.ratesUpdate
        val upAt = if (s.unionPay == null) 0L else s.unionPayFetchedAt
        if (s.settings.unionPay && RatesPolicy.due(update, trigger, upAt, now)) refreshUnionPay()
        if (s.ratesLoading) return
        val allow = trigger == Trigger.BUTTON || RatesPolicy.auto(update)
        val cbrAt = if (s.official == null) 0L else s.officialFetchedAt
        if (!RatesPolicy.due(update, trigger, cbrAt, now)) {
            refreshWeekAgo(allow)
            refreshReceiptRates(allow)
            return
        }
        _state.update { it.copy(ratesLoading = true, ratesFailed = false) }
        refreshGuide()
        viewModelScope.launch {
            val fetched = CbrClient.fetch()
            val at = System.currentTimeMillis()
            if (fetched != null) repository.saveOfficial(fetched.rates, at, fetched.source)
            _state.update {
                it.copy(
                    official = fetched?.rates ?: it.official,
                    officialFetchedAt = if (fetched != null) at else it.officialFetchedAt,
                    officialSource = fetched?.source ?: it.officialSource,
                    ratesLoading = false,
                    ratesFailed = fetched == null,
                )
            }
            refreshWeekAgo(allow)
            refreshReceiptRates(allow)
        }
    }

    /** Встроенный справочник и сохранённый скачанный — что свежее (разбор — не на главном потоке). */
    private suspend fun loadGuides(saved: String?): CountryGuides? = withContext(Dispatchers.Default) {
        val bundled = runCatching { bundledGuide() }.getOrNull()?.let(CountryGuideParser::parse)
        val downloaded = saved?.let(CountryGuideParser::parse)
        bundled?.newer(downloaded) ?: downloaded
    }

    private var guideLoading = false

    /** Справочник из репозитория: сохраняем, только если он свежее того, что уже есть. */
    private fun refreshGuide() {
        if (guideLoading) return
        guideLoading = true
        viewModelScope.launch {
            val fetched = GuideClient.fetch()
            val have = _state.value.guides
            if (fetched != null && (have == null || fetched.second.version.isAfter(have.version))) {
                repository.saveGuide(fetched.first)
                _state.update { it.copy(guides = fetched.second) }
            }
            guideLoading = false
        }
    }

    /** Курсы UnionPay: тихо, без индикатора — не скачались, так остаются прежние (или ЦБ и чеки). */
    private fun refreshUnionPay() {
        if (unionPayLoading) return
        unionPayLoading = true
        viewModelScope.launch {
            val rates = UnionPayClient.fetch()
            if (rates != null) {
                val at = System.currentTimeMillis()
                repository.saveUnionPay(rates, at)
                _state.update { it.copy(unionPay = rates, unionPayFetchedAt = at) }
            }
            unionPayLoading = false
        }
    }

    /** Настройки: сохраняются сразу; включили UnionPay, а его курсов нет — скачиваем (если не «вручную»). */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = _state.value.settings
        val next = transform(before)
        if (next == before) return
        _state.update { it.copy(settings = next) }
        viewModelScope.launch { repository.saveSettings(next) }
        if (next.unionPay && !before.unionPay && _state.value.unionPay == null && autoNetwork()) refreshUnionPay()
    }

    fun setUnionPayEnabled(value: Boolean) = updateSettings { it.copy(unionPay = value) }
    fun setRatesUpdate(value: RatesUpdate) = updateSettings { it.copy(ratesUpdate = value) }
    fun setCountryHint(value: Boolean) = updateSettings { it.copy(countryHint = value) }
    fun setTheme(value: ThemeMode) = updateSettings { it.copy(theme = value) }
    fun setMotion(value: MotionMode) = updateSettings { it.copy(motion = value) }
    /** Сами (не кнопкой) в сеть можно: обновление не ручное и не мешает «только по Wi-Fi». */
    private fun autoNetwork(): Boolean {
        val settings = _state.value.settings
        return RatesPolicy.auto(settings.ratesUpdate) && RatesPolicy.network(Trigger.RESUME, settings.wifiOnly, unmetered())
    }

    fun setWifiOnly(value: Boolean) = updateSettings { it.copy(wifiOnly = value) }
    fun setRounding(value: Rounding) = updateSettings { it.copy(rounding = value) }
    fun setKeyHaptics(value: Boolean) = updateSettings { it.copy(keyHaptics = value) }

    fun setPureBlack(value: Boolean) = updateSettings { it.copy(pureBlack = value) }

    /** Pro куплена (или тестовая покупка отменена). Функции Pro без неё выключаются. */
    fun setPro(value: Boolean) = updateSettings {
        if (value) it.copy(pro = true) else it.copy(pro = false, dynamicColor = false, appIcon = AppIcon.CLASSIC)
    }

    fun setDynamicColor(value: Boolean) = updateSettings { it.copy(dynamicColor = value && it.pro) }

    fun setAppIcon(value: AppIcon) = updateSettings { it.copy(appIcon = if (it.pro) value else AppIcon.CLASSIC) }

    /** «Показать знакомство снова». */
    fun restartOnboarding() {
        _state.update { it.copy(onboardingDone = false) }
        viewModelScope.launch { repository.saveOnboardingDone(false) }
    }

    private val historyLoading = HashSet<LocalDate>()

    /**
     * Курсы ЦБ на даты старых чеков (для «чек устарел»): качаем недостающие, не больше нескольких за раз.
     * При обновлении «вручную» — только по кнопке ([allow]).
     */
    private fun refreshReceiptRates(allow: Boolean = autoNetwork()) {
        val s = _state.value
        if (s.official == null || !allow) return
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
    private fun refreshWeekAgo(allow: Boolean) {
        val s = _state.value
        if (!allow) return
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
        private const val GUIDE_ASSET = "countries.txt"
        private const val WEEK_DAYS = 7L
        private const val WEEK_SLACK_DAYS = 3L
        private const val HISTORY_BATCH = 5
        private const val HISTORY_MAX = 30

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                MainViewModel(
                    WalletRepository(app.settingsDataStore, app.unionPayDataStore, app.ratesDataStore),
                    bundledGuide = { app.assets.open(GUIDE_ASSET).use { String(it.readBytes(), Charsets.UTF_8) } },
                    unmetered = { NetworkState.unmetered(app) },
                )
            }
        }
    }
}

internal fun divideOrZero(value: BigDecimal, divisor: BigDecimal): BigDecimal =
    if (divisor.signum() == 0) BigDecimal.ZERO else value.divide(divisor, MC)
