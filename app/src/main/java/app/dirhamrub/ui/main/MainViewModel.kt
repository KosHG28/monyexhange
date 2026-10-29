package app.dirhamrub.ui.main

import androidx.lifecycle.ViewModel
import app.dirhamrub.domain.Amounts
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.Comparison
import app.dirhamrub.domain.Converter
import app.dirhamrub.domain.Defaults
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Receipt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate

data class MainUiState(
    val selected: Card = Card.CNY,
    val rates: Map<Card, CardRate> = Defaults.rates(),
    val active: Line = Line.AED,
    val expression: Expression = Expression(),
) {
    val rate: CardRate get() = rates.getValue(selected)
    val amounts: Amounts get() = Converter.amounts(expression.value, active, rate)
    val comparison: Comparison get() = Converter.compare(amounts.aed, rates)

    /** Текст активной строки: набираемое число или итог выражения «22 + 15,5». */
    val activeText: String
        get() = when {
            expression.isEmpty -> "0"
            expression.isCompound -> Numbers.money(expression.value)
            else -> expression.currentTerm()
        }
}

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    fun press(key: Key) = _state.update { it.copy(expression = it.expression.press(key)) }

    fun selectCard(card: Card) = _state.update { s ->
        when {
            s.selected == card -> s
            // Сумма в валюте карты не должна прыгнуть: сначала переводим её в дирхамы.
            s.active == Line.CARD -> s.copy(selected = card, active = Line.AED, expression = Expression.of(s.amounts.aed))
            else -> s.copy(selected = card)
        }
    }

    fun setActive(line: Line) = _state.update { s ->
        if (s.active == line) s else s.copy(active = line, expression = Expression.of(s.amounts.of(line)))
    }

    /** Быстрая сумма в дирхамах: после «+» подставляется в выражение, иначе заменяет его. */
    fun putAed(aed: BigDecimal) = _state.update { s ->
        if (s.active == Line.AED) s.copy(expression = s.expression.putAmount(aed))
        else s.copy(active = Line.AED, expression = Expression.of(aed))
    }

    /** Цены из камеры: одна — заменяет сумму, несколько — становятся выражением «a + b + c». */
    fun putAedItems(items: List<BigDecimal>) = _state.update { s ->
        val expression = when (items.size) {
            0 -> s.expression
            1 -> Expression.of(items.single())
            else -> Expression(items.map { Numbers.plain(it) })
        }
        s.copy(active = Line.AED, expression = expression)
    }

    fun setManualRate(card: Card, rate: BigDecimal): Boolean {
        val current = _state.value.rates.getValue(card)
        val updated = current.withManualRate(rate, LocalDate.now()) ?: return false
        replaceRate(updated)
        return true
    }

    fun applyReceipt(card: Card, receipt: Receipt): Boolean {
        val current = _state.value.rates.getValue(card)
        val updated = current.withReceipt(receipt, LocalDate.now()) ?: return false
        replaceRate(updated)
        return true
    }

    /** Вернуть стартовый чек от 29 октября вместе с его курсом. */
    fun resetReceipt(card: Card) = replaceRate(Defaults.rate(card))

    private fun replaceRate(rate: CardRate) = _state.update { s ->
        s.copy(rates = s.rates + (rate.card to rate))
    }

    companion object {
        val CHIPS: List<BigDecimal> = listOf(5, 10, 22, 50, 100, 200, 500).map { BigDecimal(it) }
        val CHEAT: List<BigDecimal> = listOf(1, 5, 10, 20, 50, 100, 200, 500, 1000).map { BigDecimal(it) }

        /** Сумма в рублях по курсу карты — для подписей и шпаргалки. */
        fun rub(aed: BigDecimal, rate: CardRate): BigDecimal = aed.multiply(rate.rubPerAed, MC)
    }
}
