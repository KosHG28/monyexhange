package app.dirhamrub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.dirhamrub.domain.AppSettings
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MotionMode
import app.dirhamrub.domain.RatesUpdate
import app.dirhamrub.domain.Rounding
import app.dirhamrub.domain.ThemeMode
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.Exchanger
import app.dirhamrub.domain.wallet.ExchangerCodec
import app.dirhamrub.domain.wallet.ManualRate
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PurchaseReceipt
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletCodec
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Курсы UnionPay (~2400 пар) — отдельно от настроек: те переписываются при каждом нажатии клавиши. */
val Context.unionPayDataStore: DataStore<Preferences> by preferencesDataStore(name = "unionpay")

/** Курсы ЦБ (сегодня, неделю назад, на даты чеков) — отдельно: сохранение карты не переписывает их, и наоборот. */
val Context.ratesDataStore: DataStore<Preferences> by preferencesDataStore(name = "rates")

/** Всё, что переживает перезапуск приложения. */
data class StoredState(
    val wallet: Wallet,
    val official: OfficialRates?,
    /** Когда курсы ЦБ скачаны (мс), 0 — никогда. */
    val officialFetchedAt: Long,
    /** Курсы ЦБ неделю назад — для стрелки изменения. */
    val officialWeekAgo: OfficialRates?,
    val active: Line,
    val expression: Expression,
    /** Страна сети, для которой подсказку «переключить валюту» уже показали (приняли или закрыли). */
    val countryHandled: String?,
    /** Знакомство с приложением пройдено (или пропущено). */
    val onboardingDone: Boolean,
    /** Камера вписывает суммы в ценники (иначе — плашки). */
    val cameraEmbedded: Boolean,
    /** Курсы ЦБ на даты чеков — чтобы заметить устаревший чек. */
    val ratesHistory: Map<LocalDate, OfficialRates>,
    /** Камерой уже пользовались — можно предложить плитку «Ценник». */
    val cameraUsed: Boolean,
    /** Плитку «Ценник» уже предлагали (добавили или отказались). */
    val tileOffered: Boolean,
    /** Курсы UnionPay — для карт UnionPay и справочного курса валют без ЦБ. */
    val unionPay: UnionPayRates?,
    /** Когда курсы UnionPay скачаны (мс), 0 — никогда или со старым справочником валют. */
    val unionPayFetchedAt: Long,
    /** Откуда в последний раз пришли курсы ЦБ: «cbr.ru» или зеркало. */
    val officialSource: String?,
    /** Скачанный справочник «Чем платить» (текст файла), если он свежее встроенного. */
    val guideText: String?,
    /** Записанные курсы обменников. */
    val exchangers: List<Exchanger>,
    /** Выбор способа оплаты уже переведён на «автовыбор лучшего» (переделка «конвертер»). */
    val selectionAuto: Boolean,
    val settings: AppSettings,
)

/**
 * Хранит кошелёк (карты, чеки, валюты), курсы ЦБ, последнюю сумму и активную строку.
 * При первом запуске после обновления с «Дирхамов в рубли» (0.x) переносит две старые карты с чеками.
 */
class WalletRepository(
    private val store: DataStore<Preferences>,
    private val unionPayStore: DataStore<Preferences>,
    private val ratesStore: DataStore<Preferences>,
) {

    suspend fun load(): StoredState {
        val p = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        // Курсы ЦБ до 1.20 лежали вместе с кошельком: при первом запуске переносим их в свой файл.
        if (RATE_KEYS.any { it in p }) migrateRates(p)
        val r = ratesStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val u = unionPayStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return StoredState(
            wallet = WalletCodec.decode(p[WALLET]) ?: legacyWallet(p) ?: Wallet(),
            // Курсы, сохранённые со старым справочником валют, неполные (в 1.10 — только 14 валют):
            // показываем их, пока не скачаны свежие, но срок годности не учитываем — скачиваем сразу.
            official = WalletCodec.decodeRates(r[OFFICIAL]),
            officialFetchedAt = if (WalletCodec.isCurrentTable(r[OFFICIAL])) r[OFFICIAL_AT] ?: 0L else 0L,
            officialWeekAgo = r[OFFICIAL_WEEK]?.takeIf(WalletCodec::isCurrentTable)?.let(WalletCodec::decodeRates),
            active = when (p[ACTIVE]) {
                "RUB", "HOME" -> Line.HOME
                "CARD" -> Line.CARD
                else -> Line.SPEND
            },
            expression = Expression.parse(p[EXPRESSION]),
            countryHandled = p[COUNTRY_HANDLED],
            onboardingDone = p[ONBOARDING_DONE] ?: false,
            cameraEmbedded = p[CAMERA_EMBEDDED] ?: true,
            ratesHistory = r[RATES_HISTORY]?.takeIf(WalletCodec::isCurrentTable)?.let(WalletCodec::decodeHistory).orEmpty(),
            cameraUsed = p[CAMERA_USED] ?: false,
            tileOffered = p[TILE_OFFERED] ?: false,
            unionPay = WalletCodec.decodeUnionPay(u[UNIONPAY]),
            unionPayFetchedAt = if (WalletCodec.isCurrentTable(u[UNIONPAY])) u[UNIONPAY_AT] ?: 0L else 0L,
            officialSource = r[OFFICIAL_SOURCE],
            guideText = r[GUIDE],
            exchangers = ExchangerCodec.decode(p[EXCHANGERS]),
            selectionAuto = p[SELECTION_AUTO] == true,
            settings = AppSettings(
                unionPay = p[SET_UNIONPAY] ?: true,
                ratesUpdate = RatesUpdate.entries.firstOrNull { it.name == p[SET_RATES_UPDATE] } ?: RatesUpdate.EVERY_6H,
                countryHint = p[SET_COUNTRY_HINT] ?: true,
                theme = ThemeMode.entries.firstOrNull { it.name == p[SET_THEME] } ?: ThemeMode.SYSTEM,
                motion = MotionMode.entries.firstOrNull { it.name == p[SET_MOTION] } ?: MotionMode.SYSTEM,
                keyHaptics = p[SET_KEY_HAPTICS] ?: true,
                wifiOnly = p[SET_WIFI_ONLY] ?: false,
                rounding = Rounding.entries.firstOrNull { it.name == p[SET_ROUNDING] } ?: Rounding.WHOLE,
            ),
        )
    }

    suspend fun saveSettings(settings: AppSettings) {
        store.edit { p ->
            p[SET_UNIONPAY] = settings.unionPay
            p[SET_RATES_UPDATE] = settings.ratesUpdate.name
            p[SET_COUNTRY_HINT] = settings.countryHint
            p[SET_THEME] = settings.theme.name
            p[SET_MOTION] = settings.motion.name
            p[SET_KEY_HAPTICS] = settings.keyHaptics
            p[SET_WIFI_ONLY] = settings.wifiOnly
            p[SET_ROUNDING] = settings.rounding.name
        }
    }

    suspend fun saveUnionPay(rates: UnionPayRates, fetchedAt: Long) {
        unionPayStore.edit { p ->
            p[UNIONPAY] = WalletCodec.encodeUnionPay(rates)
            p[UNIONPAY_AT] = fetchedAt
        }
    }

    suspend fun saveOfficialWeekAgo(rates: OfficialRates) {
        ratesStore.edit { p -> p[OFFICIAL_WEEK] = WalletCodec.encodeRates(rates) }
    }

    suspend fun saveGuide(text: String) {
        ratesStore.edit { r -> r[GUIDE] = text }
    }

    suspend fun saveExchangers(list: List<Exchanger>) {
        store.edit { p -> p[EXCHANGERS] = ExchangerCodec.encode(list) }
    }

    /** Один раз при переходе на автовыбор: кошелёк без ручного выбора и отметка, что переход сделан. */
    suspend fun markSelectionAuto(wallet: Wallet) {
        store.edit { p ->
            p[WALLET] = WalletCodec.encode(wallet)
            p[SELECTION_AUTO] = true
        }
    }

    suspend fun saveCameraUsed() {
        store.edit { p -> p[CAMERA_USED] = true }
    }

    suspend fun saveTileOffered() {
        store.edit { p -> p[TILE_OFFERED] = true }
    }

    suspend fun saveRatesHistory(history: Map<LocalDate, OfficialRates>) {
        ratesStore.edit { p -> p[RATES_HISTORY] = WalletCodec.encodeHistory(history) }
    }

    suspend fun saveCameraEmbedded(value: Boolean) {
        store.edit { p -> p[CAMERA_EMBEDDED] = value }
    }

    suspend fun saveOnboardingDone(done: Boolean = true) {
        store.edit { p -> p[ONBOARDING_DONE] = done }
    }

    suspend fun saveCountryHandled(iso: String) {
        store.edit { p -> p[COUNTRY_HANDLED] = iso }
    }

    /** Карты, чеки и валюты: пишутся сразу при изменении — закрытое следом приложение их не теряет. */
    suspend fun saveWallet(wallet: Wallet) {
        store.edit { p -> p[WALLET] = WalletCodec.encode(wallet) }
    }

    /** Набираемая сумма и активная строка (меняются на каждом нажатии — пишутся с задержкой). */
    suspend fun saveInput(active: Line, expression: Expression) {
        store.edit { p ->
            p[ACTIVE] = active.name
            p[EXPRESSION] = expression.serialize()
        }
    }

    /** Всё сразу — когда приложение уходит с экрана. */
    suspend fun saveSession(wallet: Wallet, active: Line, expression: Expression) {
        store.edit { p ->
            p[WALLET] = WalletCodec.encode(wallet)
            p[ACTIVE] = active.name
            p[EXPRESSION] = expression.serialize()
        }
    }

    private suspend fun migrateRates(p: Preferences) {
        ratesStore.edit { r ->
            p[OFFICIAL]?.let { r[OFFICIAL] = it }
            p[OFFICIAL_AT]?.let { r[OFFICIAL_AT] = it }
            p[OFFICIAL_SOURCE]?.let { r[OFFICIAL_SOURCE] = it }
            p[OFFICIAL_WEEK]?.let { r[OFFICIAL_WEEK] = it }
            p[RATES_HISTORY]?.let { r[RATES_HISTORY] = it }
        }
        store.edit { e -> RATE_KEYS.forEach { e.remove(it) } }
    }

    /** Демо-состояние для скриншотов CI ([app.dirhamrub.diag.Demo]): всё сразу, курсы «только что скачаны». */
    suspend fun saveDemo(
        wallet: Wallet,
        official: OfficialRates,
        weekAgo: OfficialRates,
        unionPay: UnionPayRates,
        expression: Expression,
    ) {
        saveUnionPay(unionPay, System.currentTimeMillis())
        ratesStore.edit { r ->
            r.clear()
            r[OFFICIAL] = WalletCodec.encodeRates(official)
            r[OFFICIAL_AT] = System.currentTimeMillis()
            r[OFFICIAL_SOURCE] = CbrClient.SOURCE_CBR
            r[OFFICIAL_WEEK] = WalletCodec.encodeRates(weekAgo)
        }
        store.edit { p ->
            p.clear()
            p[WALLET] = WalletCodec.encode(wallet)
            p[ACTIVE] = Line.SPEND.name
            p[EXPRESSION] = expression.serialize()
            p[ONBOARDING_DONE] = true
            // Эмулятор CI «находится» в США — плашку «считать в USD?» на скриншотах не показываем,
            // как и предложение плитки «Ценник».
            p[COUNTRY_HANDLED] = "US"
            p[TILE_OFFERED] = true
            p[SELECTION_AUTO] = true
        }
    }

    suspend fun saveOfficial(rates: OfficialRates, fetchedAt: Long, source: String) {
        ratesStore.edit { p ->
            p[OFFICIAL] = WalletCodec.encodeRates(rates)
            p[OFFICIAL_AT] = fetchedAt
            p[OFFICIAL_SOURCE] = source
        }
    }

    /** Карты «в тенге» и «в юанях» из версий 0.x: чек перевода в рублях и чек покупки в дирхамах. */
    private fun legacyWallet(p: Preferences): Wallet? {
        val cards = listOfNotNull(
            legacyCard(p, "kzt", "Карта в тенге", Currency.KZT, style = 0),
            legacyCard(p, "cny", "Карта в юанях", Currency.CNY, style = 1),
        )
        if (cards.isEmpty()) return null
        val selected = if (p[stringPreferencesKey("selected_card")] == "KZT") "kzt" else "cny"
        return Wallet(
            cards = cards,
            home = Currency.RUB,
            spend = if (p[stringPreferencesKey("price_currency")] == "KZT") Currency.KZT else Currency.AED,
            selectedId = selected.takeIf { id -> cards.any { it.id == id } } ?: cards.first().id,
        )
    }

    private fun legacyCard(p: Preferences, prefix: String, name: String, currency: Currency, style: Int): BankCard? {
        fun dec(key: String) = p[stringPreferencesKey("${prefix}_$key")]?.toBigDecimalOrNull()
        val rubSent = dec("rub_sent") ?: return null
        val cardGot = dec("card_got") ?: return null
        val cardPaid = dec("card_paid") ?: return null
        val aedPaid = dec("aed_paid") ?: return null
        val date = p[longPreferencesKey("${prefix}_date")]?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
            ?: LocalDate.of(2026, 10, 29)
        val manualRate = dec("rub_per_aed")?.takeIf { p[stringPreferencesKey("${prefix}_source")] == "MANUAL" }
        return BankCard(
            id = prefix,
            name = name,
            currency = currency,
            style = style,
            transfer = TransferReceipt(rubSent, Currency.RUB, cardGot, date),
            purchases = listOf(PurchaseReceipt(Currency.AED, cardPaid, aedPaid, date)),
            manual = listOfNotNull(manualRate?.let { ManualRate(Currency.AED, Currency.RUB, it, date) }),
        )
    }

    private companion object {
        val WALLET = stringPreferencesKey("wallet")
        val OFFICIAL = stringPreferencesKey("official_rates")
        val OFFICIAL_AT = longPreferencesKey("official_fetched_at")
        val ACTIVE = stringPreferencesKey("active_line")
        val EXPRESSION = stringPreferencesKey("expression")
        val COUNTRY_HANDLED = stringPreferencesKey("country_handled")
        val OFFICIAL_WEEK = stringPreferencesKey("official_week_ago")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CAMERA_EMBEDDED = booleanPreferencesKey("camera_embedded")
        val RATES_HISTORY = stringPreferencesKey("rates_history")
        val CAMERA_USED = booleanPreferencesKey("camera_used")
        val TILE_OFFERED = booleanPreferencesKey("tile_offered")
        val OFFICIAL_SOURCE = stringPreferencesKey("official_source")
        val GUIDE = stringPreferencesKey("country_guide")
        val EXCHANGERS = stringPreferencesKey("exchangers")

        /** Ключи курсов ЦБ: живут в своём файле (до 1.20 — в общем с кошельком). */
        val RATE_KEYS: List<Preferences.Key<*>> = listOf(OFFICIAL, OFFICIAL_AT, OFFICIAL_SOURCE, OFFICIAL_WEEK, RATES_HISTORY)
        val SET_UNIONPAY = booleanPreferencesKey("settings_unionpay")
        val SET_RATES_UPDATE = stringPreferencesKey("settings_rates_update")
        val SET_COUNTRY_HINT = booleanPreferencesKey("settings_country_hint")
        val SET_THEME = stringPreferencesKey("settings_theme")
        val SET_MOTION = stringPreferencesKey("settings_motion")
        val SET_WIFI_ONLY = booleanPreferencesKey("settings_wifi_only")
        val SET_ROUNDING = stringPreferencesKey("settings_rounding")
        val SET_KEY_HAPTICS = booleanPreferencesKey("settings_key_haptics")
        val SELECTION_AUTO = booleanPreferencesKey("selection_auto")
        val UNIONPAY = stringPreferencesKey("unionpay_rates")
        val UNIONPAY_AT = longPreferencesKey("unionpay_fetched_at")
    }
}
