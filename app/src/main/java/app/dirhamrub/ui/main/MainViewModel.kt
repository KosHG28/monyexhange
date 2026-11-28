package app.dirhamrub.ui.main

import app.dirhamrub.domain.wallet.withAppearance

import app.dirhamrub.domain.AppIcon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dirhamrub.data.NetworkRateSources
import app.dirhamrub.data.RateSources
import app.dirhamrub.data.recoverable
import app.dirhamrub.data.CbrClient
import app.dirhamrub.data.GuideClient
import app.dirhamrub.data.NetworkState
import app.dirhamrub.data.UnionPayClient
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.data.ratesDataStore
import app.dirhamrub.data.settingsDataStore
import app.dirhamrub.data.unionPayDataStore
import app.dirhamrub.domain.AppSettings
import app.dirhamrub.domain.ProPromo
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.InputLimit
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
import app.dirhamrub.domain.wallet.CardMaterial
import app.dirhamrub.domain.wallet.CardQuote
import app.dirhamrub.domain.wallet.CountryGuideParser
import app.dirhamrub.domain.wallet.CountryGuides
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.InputSnapshot
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.QuoteCache
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.ReceiptAge
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletQuote
import java.math.BigDecimal
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val inputLimit: InputLimit? = null,
    val inputSnapshot: InputSnapshot? = null,
    val pendingConversion: InputSnapshot? = null,
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
    val previousUnionPay: UnionPayRates? = null,
    val settings: AppSettings = AppSettings(),
    /** Когда скачаны курсы ЦБ (мс, 0 — никогда) и откуда — для настроек. */
    val officialFetchedAt: Long = 0L,
    val officialSource: String? = null,
    val unionPayFetchedAt: Long = 0L,
    val unionPayLoading: Boolean = false,
    val unionPayFailed: Boolean = false,
    /** Справочник «Чем платить в стране»: встроенный или скачанный, что свежее. */
    val guides: CountryGuides? = null,
    val pricing: QuoteCache = QuoteCache(),
) {
    /** Справочные курсы: ЦБ, а валюты без ЦБ — по UnionPay к доллару и ЦБ доллара (они в [OfficialRates.derived]). */
    val reference: OfficialRates? by lazy { pricing.reference(official, unionPay) }

    val ratesRefreshing: Boolean get() = ratesLoading || unionPayLoading

    val quote: WalletQuote by lazy { pricing.quote(wallet, reference, unionPay) }
    /** Чем считаем: выбранный вручную способ, а без ручного выбора — самый выгодный ([Wallet.pick]). */
    val selectedCard: BankCard? get() = wallet.pick(quote.best?.card?.id)
    val selectedQuote: CardQuote? get() = quote.of(selectedCard?.id)

    /** Курс для расчёта: выбранная карта, а без карт — курс ЦБ. */
    val homePerUnit: BigDecimal? get() = selectedQuote?.homePerUnit ?: quote.official
    val cardPerUnit: BigDecimal? get() = selectedQuote?.cardPerUnit?.takeIf { it.signum() > 0 }

    /** Строка валюты карты нужна, только если она отличается от валют трат и итога. */
    val showCardLine: Boolean
        get() = cardPerUnit != null && selectedCard!!.currency != wallet.spend && selectedCard!!.currency != wallet.home

    val displaySpend: BigDecimal?
        get() = totals.spend.takeUnless { active == Line.HOME && homePerUnit == null }

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

    val inputCurrency: Currency get() = if (active == Line.HOME) wallet.home else wallet.spend
    val fractionDigits: Int get() = inputCurrency.digits.coerceAtLeast(2)

    /** Текст активной строки: набираемое число или итог выражения «22 + 15,5». */
    val activeText: String
        get() = when {
            expression.isEmpty -> "0"
            expression.isCompound -> if (fractionDigits > 2) Numbers.groupInput(Numbers.plain(expression.value, fractionDigits)) else Numbers.money(expression.value)
            expression.fresh && expression.terms.last().length > 16 ->
                Numbers.groupInput(Numbers.plain(expression.value, fractionDigits))
            else -> expression.currentTerm()
        }
}

class MainViewModel(
    private val repository: WalletRepository,
    /** Встроенный справочник стран (`assets/countries.txt`). */
    private val bundledGuide: () -> String? = { null },
    /** Сеть сейчас безлимитная (Wi-Fi, Ethernet) — для «только по Wi-Fi». */
    private val unmetered: () -> Boolean = { true },
    private val guideDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val sources: RateSources = NetworkRateSources,
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    val navigation = _state.distinctUntilChanged(::sameNavigationState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value)
    val settings = _state.map { it.settings }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _state.value.settings)
    internal val widgetUpdates = repository.widgetUpdates
    private val _storageErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val storageErrors = _storageErrors.asSharedFlow()
    private val sessionWrites = Mutex()
    private var localGuideJob: Job? = null
    private val historyLoading = HashSet<LocalDate>()
    private val historyWrites = Mutex()

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
                inputSnapshot = stored.inputSnapshot,
                pendingConversion = stored.pendingConversion,
                countryHandled = stored.countryHandled,
                // Знакомство — только для нового пользователя: у перешедших со старых версий карты уже есть.
                onboardingDone = stored.onboardingDone || stored.wallet.cards.isNotEmpty(),
                cameraEmbedded = stored.cameraEmbedded,
                ratesHistory = stored.ratesHistory,
                cameraUsed = stored.cameraUsed,
                tileOffered = stored.tileOffered,
                unionPay = stored.unionPay,
                previousUnionPay = stored.previousUnionPay,
                settings = stored.settings,
                officialFetchedAt = stored.officialFetchedAt,
                officialSource = stored.officialSource,
                unionPayFetchedAt = stored.unionPayFetchedAt,
            ).resolvePendingConversion()
            // Карты и чеки пишем сразу: сохранил карту и тут же закрыл приложение — она не пропадёт.
            // Изменения, не касающиеся кошелька (курсы загружаются, настройки), запись не вызывают.
            launch(start = CoroutineStart.UNDISPATCHED) {
                _state.map { it.wallet }.distinctUntilChanged().drop(1).collect {
                    sessionWrites.withLock { writeSession(_state.value) }
                }
            }
            // Набираемую сумму — не чаще раза в 300 мс: collectLatest отменяет отложенную запись при новом нажатии.
            launch(start = CoroutineStart.UNDISPATCHED) {
                _state.map { listOf(it.active, it.expression, it.inputSnapshot, it.pendingConversion) }
                    .distinctUntilChanged().drop(1).collectLatest {
                        delay(SAVE_DELAY_MS)
                        sessionWrites.withLock {
                            val latest = _state.value
                            if (!tryStorage { repository.saveInput(latest.active, latest.expression, latest.inputSnapshot, latest.pendingConversion) }) {
                                _storageErrors.tryEmit(Unit)
                            }
                        }
                    }
            }
            // Справочник не задерживает готовый калькулятор; завершение меняет только guides.
            localGuideJob = viewModelScope.launch {
                val guides = loadGuides(stored.guideText)
                _state.update { it.withLoadedGuides(guides) }
            }
            if (!tryStorage {
                    if (!stored.selectionAuto) repository.markSelectionAuto(_state.value.wallet)
                    repository.dropLegacy()
                }) _storageErrors.tryEmit(Unit)
            refresh(Trigger.LAUNCH)
        }
    }

    /** Приложение ушло с экрана: дописываем то, что ждало своей задержки (последние нажатия). */
    fun flush() {
        // Capture after dispatch: a queued pause must not persist an older expression over newer input.
        viewModelScope.launch {
            withContext(NonCancellable) {
                sessionWrites.withLock {
                    val latest = _state.value
                    if (latest.loaded) writeSession(latest)
                }
            }
        }
    }

    private suspend fun tryStorage(write: suspend () -> Unit): Boolean = try {
        write()
        true
    } catch (_: IOException) {
        false
    }

    private suspend fun writeSession(latest: MainUiState, notify: Boolean = true): Boolean {
        val saved = tryStorage {
            repository.saveSession(latest.wallet, latest.active, latest.expression, latest.inputSnapshot, latest.pendingConversion)
        }
        if (!saved && notify) _storageErrors.tryEmit(Unit)
        return saved
    }

    fun press(key: Key) = _state.update { state ->
        val limit = state.expression.limitFor(key, state.fractionDigits)
        val next = state.expression.press(key, state.fractionDigits)
        if (next == state.expression && limit == null && key != Key.Clear) state
        else state.copy(
            expression = next,
            inputLimit = limit,
            inputSnapshot = if (limit == null) null else state.inputSnapshot,
            pendingConversion = if (limit == null) null else state.pendingConversion,
        )
    }

    /** Коснулись поля «цена» ([Line.SPEND]) или «стоит вам» ([Line.HOME]): вводим в нём, сумма не меняется. */
    fun setActive(line: Line) = _state.update { s ->
        if (s.active == line || line == Line.CARD || s.homePerUnit == null) return@update s
        val original = s.reversibleInput()
        if (original?.line == line) {
            s.copy(active = line, expression = original.expression, inputSnapshot = null, inputLimit = null)
        } else {
            s.copy(
                active = line,
                expression = Expression.of(s.totals.of(line) ?: BigDecimal.ZERO),
                inputSnapshot = InputSnapshot(s.inputCurrency, s.active, s.expression, s.totals.home),
                inputLimit = null,
            )
        }
    }

    /** Коснулись плитки способа: выбрать его; лучший — снова автовыбор ([Wallet.choose]). */
    fun selectCard(id: String) = _state.update { s ->
        val next = s.wallet.choose(id, s.quote.best?.card?.id)
        if (next == s.wallet) s else s.copy(wallet = next).resolvePendingConversion()
    }

    /**
     * Другая валюта трат: сохраняем сумму в итоговой валюте и пересчитываем в новую валюту. [place] — страна,
     * если её выбрали в списке стран (та же валюта, другая страна — меняется только шапка).
     */
    fun setSpend(currency: Currency, place: String? = null) {
        _state.update { s ->
            if (s.wallet.spend == currency) return@update s.copy(wallet = s.wallet.withSpend(currency, place))
            val source = s.pendingConversion ?: s.reversibleInput()
                ?: InputSnapshot(s.inputCurrency, s.active, s.expression, s.totals.home)
            val next = s.copy(
                wallet = s.wallet.withSpend(currency, place), active = Line.SPEND,
                inputSnapshot = null, inputLimit = null,
            )
            when {
                source.line == Line.SPEND && source.currency == currency ->
                    next.copy(expression = source.expression, pendingConversion = null)
                source.expression.isEmpty -> next.copy(expression = Expression(), pendingConversion = null)
                next.homePerUnit != null && source.homeAmount != null -> next.copy(
                    expression = Expression.of(divideOrZero(source.homeAmount, next.homePerUnit!!)),
                    pendingConversion = null,
                )
                else -> next.copy(expression = Expression(), pendingConversion = source).resolvePendingConversion()
            }
        }
    }

    /** Другая итоговая валюта: сумма в валюте трат остаётся. */
    fun setHome(currency: Currency) {
        _state.update { s ->
            if (s.wallet.home == currency) s
            else {
                val original = s.reversibleInput()?.takeIf { it.line == Line.SPEND }?.expression
                val expression = if (s.active == Line.SPEND) s.expression else original ?: Expression.of(s.totals.spend)
                val pending = s.pendingConversion?.copy(homeAmount = null)
                    ?: if (s.active == Line.HOME && s.homePerUnit == null && !s.expression.isEmpty) {
                        InputSnapshot(s.wallet.home, Line.HOME, s.expression, null)
                    } else null
                s.copy(
                    wallet = s.wallet.copy(home = currency), active = Line.SPEND, expression = expression,
                    inputSnapshot = null, inputLimit = null,
                    pendingConversion = pending,
                ).resolvePendingConversion()
            }
        }
    }

    /** Быстрая сумма в валюте трат: после «+» подставляется в выражение, иначе заменяет его. */
    fun putAmount(amount: BigDecimal) = _state.update { s ->
        val expression = if (s.active == Line.SPEND) s.expression.putAmount(amount) else Expression.of(amount)
        s.copy(active = Line.SPEND, expression = expression, inputSnapshot = null, inputLimit = null, pendingConversion = null)
    }

    /** Цены из камеры: одна — заменяет сумму, несколько — становятся выражением «a + b + c». */
    fun putItems(items: List<BigDecimal>) = _state.update { s ->
        if (items.isEmpty()) return@update s
        val expression = if (items.size == 1) Expression.of(items.single())
            else Expression(items.map(Numbers::exactInput))
        s.copy(active = Line.SPEND, expression = expression, inputSnapshot = null, inputLimit = null, pendingConversion = null)
    }

    /** Подсказка «вы в другой стране»: [accept] — переключить валюту трат; больше для этой страны не показываем. */
    fun handleCountry(iso: String, accept: Currency?) {
        if (accept != null) setSpend(accept, iso)
        _state.update { it.copy(countryHandled = iso) }
        viewModelScope.launch { repository.saveCountryHandled(iso) }
    }

    suspend fun saveCard(card: BankCard): Boolean = commitWallet { s ->
        val current = s.wallet.cards.firstOrNull { it.id == card.id }
        val rebased = if (current == null) card else card.copy(
            style = current.style, customColor = current.customColor, material = current.material,
        )
        s.copy(wallet = s.wallet.upsert(rebased, s.reference)).resolvePendingConversion()
    }

    suspend fun deleteCard(id: String): Boolean = commitWallet { s ->
        val wallet = s.wallet.remove(id, s.reference)
        s.copy(wallet = wallet).resolvePendingConversion()
    }

    private suspend fun commitWallet(change: (MainUiState) -> MainUiState): Boolean = sessionWrites.withLock {
        val before = _state.value
        val saved = change(before)
        if (!writeSession(saved, notify = false)) return@withLock false
        _state.update { current -> if (current === before) saved else change(current) }
        true
    }

    fun reorderPaymentWays(ids: List<String>) = _state.update { s ->
        s.copy(wallet = s.wallet.reorderPayments(ids))
    }

    fun reorderWallet(ids: List<String>) = _state.update { s ->
        s.copy(wallet = s.wallet.reorderPayments(ids, entireWallet = true))
    }

    /** Пустая карта для редактора: валюта трат по умолчанию — первая, которой ещё нет среди карт. */
    fun newCard(): BankCard {
        val w = _state.value.wallet
        val currency = listOf(w.spend, w.home).firstOrNull { c -> w.cards.none { it.currency == c } } ?: w.spend
        return BankCard(id = UUID.randomUUID().toString(), name = "", currency = currency, style = w.nextStyle())
    }

    /** Кнопка «Обновить» (строка ЦБ, настройки): курсы ЦБ и UnionPay скачиваются всегда. */
    fun refreshRates() = refresh(Trigger.BUTTON)

    /** Prepare expensive rates off-main; CAS retries against fresh input instead of replacing it. */
    private suspend fun publishMarket(change: (MainUiState) -> MainUiState) {
        while (true) {
            val before = _state.value
            val prepared = withContext(calculationDispatcher) { change(before).resolvePendingConversion() }
            if (_state.compareAndSet(before, prepared)) return
        }
    }

    /** Вернулись в приложение: курсы старше 6 часов — скачиваем, если так в настройках. */
    fun onResume() {
        if (_state.value.loaded) refresh(Trigger.RESUME)
    }

    /** Курсы ЦБ и UnionPay — по настройке «Обновлять курсы» ([RatesPolicy]). */
    private fun refresh(trigger: Trigger) {
        val s = _state.value
        // «Только по Wi-Fi»: по мобильной сети сами не ходим, кнопкой — всегда.
        if (!RatesPolicy.network(trigger, s.settings.wifiOnly, unmetered())) return
        val manual = trigger == Trigger.BUTTON
        if (manual && s.ratesRefreshing) return
        val now = System.currentTimeMillis()
        val update = s.settings.ratesUpdate
        val upAt = if (s.unionPay == null) 0L else s.unionPayFetchedAt
        if (RatesPolicy.due(update, trigger, upAt, now)) refreshUnionPay()
        if (s.ratesLoading) return
        val cbrAt = if (s.official == null) 0L else s.officialFetchedAt
        if (!RatesPolicy.due(update, trigger, cbrAt, now)) return
        _state.update { it.copy(ratesLoading = true, ratesFailed = false) }
        refreshGuide()
        viewModelScope.launch {
            try {
                val incoming = sources.official()
                val cached = _state.value.official
                val fetched = incoming?.takeUnless { cached != null && it.rates.date.isBefore(cached.date) }
                val at = System.currentTimeMillis()
                val rates = fetched?.let { repository.saveOfficial(it.rates.withCachedPrevious(cached), at, it.source) }
                publishMarket {
                    it.copy(
                        official = rates?.withCachedPrevious(it.official) ?: it.official,
                        officialFetchedAt = if (fetched != null) at else it.officialFetchedAt,
                        officialSource = fetched?.source ?: it.officialSource,
                        ratesLoading = false,
                        ratesFailed = incoming == null,
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(ratesFailed = true) }
            } finally { _state.update { it.copy(ratesLoading = false) } }
            if (manual) {
                refreshOfficialPrevious()
                refreshWeekAgo()
                refreshReceiptRates()
            }
        }
    }

    private var previousLoading = false

    private fun refreshOfficialPrevious() {
        val current = _state.value.official ?: return
        if (current.previousRubPerUnit.isNotEmpty() || previousLoading) return
        previousLoading = true
        viewModelScope.launch {
            try {
                val previous = sources.onDate(current.date.minusDays(1))
                    ?.takeIf { it.date.isBefore(current.date) } ?: return@launch
                repository.saveOfficialPrevious(current.date, previous.rubPerUnit)
                _state.update { state ->
                    val table = state.official
                    if (table?.date == current.date && table.previousRubPerUnit.isEmpty()) {
                        state.copy(official = table.copy(previousRubPerUnit = previous.rubPerUnit))
                    } else state
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* The trend stays hidden when yesterday's table is unavailable. */
            } finally {
                previousLoading = false
            }
        }
    }

    /** Встроенный справочник и сохранённый скачанный — что свежее (разбор — не на главном потоке). */
    private suspend fun loadGuides(saved: String?): CountryGuides? = withContext(guideDispatcher) {
        val bundled = recoverable { bundledGuide() }?.let(CountryGuideParser::parse)
        val downloaded = saved?.let(CountryGuideParser::parse)
        bundled?.newer(downloaded) ?: downloaded
    }

    private var guideLoading = false

    /** Справочник из репозитория: сохраняем, только если он свежее того, что уже есть. */
    private fun refreshGuide() {
        if (guideLoading) return
        guideLoading = true
        viewModelScope.launch {
            try {
                val fetched = sources.guide()
                // Дожидается только обновление справочника: иначе старый сетевой ответ может
                // затереть свежую сохранённую копию до окончания её фонового разбора.
                localGuideJob?.join()
                val have = _state.value.guides
                if (fetched != null && (have == null || fetched.second.version.isAfter(have.version))) {
                    repository.saveGuide(fetched.first)
                    _state.update { it.withLoadedGuides(fetched.second) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Keep the validated local guide. */
            } finally { guideLoading = false }
        }
    }

    private fun refreshUnionPay() {
        if (_state.value.unionPayLoading) return
        _state.update { it.copy(unionPayLoading = true, unionPayFailed = false) }
        viewModelScope.launch {
            try {
                val rates = sources.unionPay()
                val current = _state.value
                val cached = current.unionPay
                if (rates != null && (cached == null || !rates.date.isBefore(cached.date))) {
                    val previous = when (cached?.date) {
                        rates.date -> current.previousUnionPay
                        rates.date.minusDays(1) -> cached.copy(rates = cached.rates.filterKeys { it == Currency.USD })
                        else -> null
                    }
                    val at = System.currentTimeMillis()
                    repository.saveUnionPay(rates, at, previous)
                    publishMarket { it.copy(unionPay = rates, previousUnionPay = previous, unionPayFetchedAt = at) }
                } else {
                    _state.update { it.copy(unionPayFailed = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(unionPayFailed = true) }
            } finally { _state.update { it.copy(unionPayLoading = false) } }
        }
    }

    /** Настройки сохраняются сразу. */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = _state.value.settings
        val next = transform(before)
        if (next == before) return
        _state.update { it.copy(settings = next) }
        viewModelScope.launch {
            if (!tryStorage { repository.saveSettings(next) }) _storageErrors.tryEmit(Unit)
        }
    }

    fun setRatesUpdate(value: RatesUpdate) = updateSettings { it.copy(ratesUpdate = value) }
    fun setTheme(value: ThemeMode) = updateSettings { it.copy(theme = value) }
    fun setMotion(value: MotionMode) = updateSettings { it.copy(motion = value) }
    fun setWifiOnly(value: Boolean) = updateSettings { it.copy(wifiOnly = value) }
    fun setRounding(value: Rounding) = updateSettings { it.copy(rounding = value) }
    fun setKeyHaptics(value: Boolean) = updateSettings { it.copy(keyHaptics = value) }

    fun setPureBlack(value: Boolean) = updateSettings { it.copy(pureBlack = value && it.pro) }

    /** Only appearance changes; newer receipts and current selection remain intact. */
    fun setCardAppearance(id: String, style: Int, customColor: Int?, material: CardMaterial) {
        _state.update { s -> if (s.settings.pro) s.copy(wallet = s.wallet.withAppearance(id, style, customColor, material)) else s }
    }

    /** Проверка кода — до сохранения; состояние Pro переживает перезапуск через WalletRepository. */
    fun activatePro(code: String): Boolean {
        if (!ProPromo.accepts(code)) return false
        updateSettings { ProPromo.activate(it, code) }
        return true
    }

    fun setDynamicColor(value: Boolean) = updateSettings { it.copy(dynamicColor = value) }

    fun setAppIcon(value: AppIcon) = updateSettings { it.copy(appIcon = if (it.pro) value else AppIcon.CLASSIC) }

    /** «Показать знакомство снова». */
    fun restartOnboarding() {
        _state.update { it.copy(onboardingDone = false) }
        viewModelScope.launch { repository.saveOnboardingDone(false) }
    }

    /**
     * Архив загружается только по явной команде «Обновить».
     */
    private fun refreshReceiptRates() {
        val s = _state.value
        if (s.official == null) return
        val today = LocalDate.now()
        val relevant = s.wallet.cards
            .flatMap { ReceiptAge.datesToCheck(it, s.wallet.spend, s.wallet.home, today) }
            .distinct().sortedDescending().take(HISTORY_MAX)
        val missing = relevant.filter { it !in s.ratesHistory && it !in historyLoading }
        if (missing.isEmpty()) return
        historyLoading += missing
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<LocalDate, OfficialRates>()
                for (batch in missing.chunked(HISTORY_BATCH)) {
                    val loaded = coroutineScope {
                        batch.map { day ->
                            async {
                                val rates = try {
                                    sources.onDate(day)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                                rates?.let { day to it }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    updates.putAll(loaded)
                }
                if (updates.isNotEmpty()) historyWrites.withLock {
                    val previous = _state.value.ratesHistory
                    val recent = (previous + updates).entries.sortedByDescending { it.key }.take(HISTORY_MAX)
                    val history = recent.associate { it.key to it.value }
                    if (history != previous) {
                        repository.saveRatesHistory(history)
                        _state.update { it.copy(ratesHistory = history) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Retry still-missing dates on the next refresh. */
            } finally { historyLoading.removeAll(missing) }
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
            try {
                val rates = sources.onDate(target)
                if (rates != null && _state.value.official?.date?.minusDays(WEEK_DAYS) == target) {
                    repository.saveOfficialWeekAgo(rates)
                    _state.update { if (it.official?.date?.minusDays(WEEK_DAYS) == target) it.copy(officialWeekAgo = rates) else it }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Keep the previous reference. */
            } finally {
                weekLoading = false
                if (_state.value.official?.date?.minusDays(WEEK_DAYS) != target) refreshWeekAgo()
            }
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
    fun finishOnboarding(card: BankCard? = null, spendAmount: BigDecimal? = null) {
        // Одна публикация состояния: главный сразу получает карту, введённую цену и валюту ввода.
        _state.update { it.withCompletedOnboarding(card, spendAmount) }
        viewModelScope.launch {
            if (!tryStorage { repository.saveOnboardingDone() }) _storageErrors.tryEmit(Unit)
        }
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

private fun MainUiState.resolvePendingConversion(): MainUiState {
    val pending = pendingConversion ?: return this
    val rate = homePerUnit ?: return this
    val sourceValue = pending.homeAmount ?: when (pending.line) {
        Line.SPEND -> copy(wallet = wallet.withSpend(pending.currency)).homePerUnit
        else -> if (pending.currency == wallet.home) BigDecimal.ONE else reference?.cross(pending.currency, wallet.home)
    }?.let { pending.expression.value.multiply(it, MC) } ?: return this
    return copy(
        active = Line.SPEND, expression = Expression.of(divideOrZero(sourceValue, rate)),
        pendingConversion = null, inputSnapshot = null, inputLimit = null,
    )
}

private fun MainUiState.reversibleInput(): InputSnapshot? {
    val original = inputSnapshot ?: return null
    val rate = homePerUnit ?: return null
    val expected = when (original.line) {
        Line.SPEND -> original.expression.value.multiply(rate, MC)
        Line.HOME -> divideOrZero(original.expression.value, rate)
        Line.CARD -> return null
    }
    return original.takeIf { expected.compareTo(expression.value) == 0 }
}

internal fun divideOrZero(value: BigDecimal, divisor: BigDecimal): BigDecimal =
    if (divisor.signum() == 0) BigDecimal.ZERO else value.divide(divisor, MC)

/** Два независимых источника могут завершиться в любом порядке, не откатывая справочник или ввод. */
internal fun MainUiState.withLoadedGuides(incoming: CountryGuides?): MainUiState =
    copy(guides = guides?.newer(incoming) ?: incoming)
