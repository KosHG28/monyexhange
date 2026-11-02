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
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ManualRate
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.PurchaseReceipt
import app.dirhamrub.domain.wallet.TransferReceipt
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletCodec
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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
)

/**
 * Хранит кошелёк (карты, чеки, валюты), курсы ЦБ, последнюю сумму и активную строку.
 * При первом запуске после обновления с «Дирхамов в рубли» (0.x) переносит две старые карты с чеками.
 */
class WalletRepository(private val store: DataStore<Preferences>) {

    suspend fun load(): StoredState {
        val p = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return StoredState(
            wallet = WalletCodec.decode(p[WALLET]) ?: legacyWallet(p) ?: Wallet(),
            official = WalletCodec.decodeRates(p[OFFICIAL]),
            officialFetchedAt = p[OFFICIAL_AT] ?: 0L,
            officialWeekAgo = WalletCodec.decodeRates(p[OFFICIAL_WEEK]),
            active = when (p[ACTIVE]) {
                "RUB", "HOME" -> Line.HOME
                "CARD" -> Line.CARD
                else -> Line.SPEND
            },
            expression = Expression.parse(p[EXPRESSION]),
            countryHandled = p[COUNTRY_HANDLED],
            onboardingDone = p[ONBOARDING_DONE] ?: false,
        )
    }

    suspend fun saveOfficialWeekAgo(rates: OfficialRates) {
        store.edit { p -> p[OFFICIAL_WEEK] = WalletCodec.encodeRates(rates) }
    }

    suspend fun saveOnboardingDone() {
        store.edit { p -> p[ONBOARDING_DONE] = true }
    }

    suspend fun saveCountryHandled(iso: String) {
        store.edit { p -> p[COUNTRY_HANDLED] = iso }
    }

    suspend fun saveSession(wallet: Wallet, active: Line, expression: Expression) {
        store.edit { p ->
            p[WALLET] = WalletCodec.encode(wallet)
            p[ACTIVE] = active.name
            p[EXPRESSION] = expression.serialize()
        }
    }

    suspend fun saveOfficial(rates: OfficialRates, fetchedAt: Long) {
        store.edit { p ->
            p[OFFICIAL] = WalletCodec.encodeRates(rates)
            p[OFFICIAL_AT] = fetchedAt
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
    }
}
