package app.dirhamrub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.Defaults
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.RateSource
import app.dirhamrub.domain.Receipt
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Всё, что переживает перезапуск приложения. */
data class StoredState(
    val selected: Card,
    val rates: Map<Card, CardRate>,
    val active: Line,
    val expression: Expression,
)

/**
 * Хранит выбранную карту, курсы с чеками, источником и датой для обеих карт,
 * последнюю сумму (выражение) и активную строку. Числа — строками BigDecimal, без потерь.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    suspend fun load(): StoredState = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { read(it) }
        .first()

    suspend fun save(state: StoredState) {
        store.edit { p ->
            p[SELECTED] = state.selected.name
            p[ACTIVE] = state.active.name
            p[EXPRESSION] = state.expression.serialize()
            for (rate in state.rates.values) write(p, rate)
        }
    }

    private fun read(p: Preferences): StoredState = StoredState(
        selected = enumOr(p[SELECTED], Card.CNY),
        rates = Card.entries.associateWith { readRate(p, it) },
        active = enumOr(p[ACTIVE], Line.AED),
        expression = Expression.parse(p[EXPRESSION]),
    )

    private fun readRate(p: Preferences, card: Card): CardRate {
        val k = keys(card)
        val receipt = Receipt(
            rubSent = p[k.rubSent]?.toBigDecimalOrNull() ?: return Defaults.rate(card),
            cardGot = p[k.cardGot]?.toBigDecimalOrNull() ?: return Defaults.rate(card),
            cardPaid = p[k.cardPaid]?.toBigDecimalOrNull() ?: return Defaults.rate(card),
            aedPaid = p[k.aedPaid]?.toBigDecimalOrNull() ?: return Defaults.rate(card),
        )
        val rate = p[k.rate]?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return Defaults.rate(card)
        val date = p[k.date]?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() } ?: Defaults.DATE
        return CardRate(card, receipt, rate, enumOr(p[k.source], RateSource.RECEIPTS), date)
    }

    private fun write(p: MutablePreferences, rate: CardRate) {
        val k = keys(rate.card)
        p[k.rate] = rate.rubPerAed.toPlainString()
        p[k.source] = rate.source.name
        p[k.date] = rate.date.toEpochDay()
        p[k.rubSent] = rate.receipt.rubSent.toPlainString()
        p[k.cardGot] = rate.receipt.cardGot.toPlainString()
        p[k.cardPaid] = rate.receipt.cardPaid.toPlainString()
        p[k.aedPaid] = rate.receipt.aedPaid.toPlainString()
    }

    private class CardKeys(prefix: String) {
        val rate = stringPreferencesKey("${prefix}_rub_per_aed")
        val source = stringPreferencesKey("${prefix}_source")
        val date = longPreferencesKey("${prefix}_date")
        val rubSent = stringPreferencesKey("${prefix}_rub_sent")
        val cardGot = stringPreferencesKey("${prefix}_card_got")
        val cardPaid = stringPreferencesKey("${prefix}_card_paid")
        val aedPaid = stringPreferencesKey("${prefix}_aed_paid")
    }

    private companion object {
        val SELECTED = stringPreferencesKey("selected_card")
        val ACTIVE = stringPreferencesKey("active_line")
        val EXPRESSION = stringPreferencesKey("expression")
        val KZT = CardKeys("kzt")
        val CNY = CardKeys("cny")

        fun keys(card: Card) = when (card) {
            Card.KZT -> KZT
            Card.CNY -> CNY
        }

        inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
