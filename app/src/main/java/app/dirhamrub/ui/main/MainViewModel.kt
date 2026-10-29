package app.dirhamrub.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.dirhamrub.data.SettingsRepository
import app.dirhamrub.data.StoredState
import app.dirhamrub.data.settingsDataStore
import app.dirhamrub.domain.Amounts
import app.dirhamrub.domain.Card
import app.dirhamrub.domain.CardRate
import app.dirhamrub.domain.Comparison
import app.dirhamrub.domain.Converter
import app.dirhamrub.domain.Defaults
import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Key
import app.dirhamrub.domain.Line
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Pricing
import app.dirhamrub.domain.Receipt
import app.dirhamrub.domain.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

data class MainUiState(
    /** Сохранённое состояние уже прочитано из DataStore. */
    val loaded: Boolean = false,
    val selected: Card = Card.CNY,
    val rates: Map<Card, CardRate> = Defaults.rates(),
    /** Валюта вводимых цен: дирхамы или тенге. */
    val source: Source = Source.AED,
    val active: Line = Line.SOURCE,
    val expression: Expression = Expression(),
) {
    val rate: CardRate get() = rates.getValue(selected)
    val amounts: Amounts get() = Converter.amounts(expression.value, active, selected, source, rates)
    val comparison: Comparison get() = Converter.compare(amounts.source, source, rates)

    /** Рубли за единицу валюты цен по выбранной карте. */
    val rubPerUnit: BigDecimal get() = Pricing.rubPerUnit(selected, source, rates)

    /** Строка «валюта карты» не нужна, если карта в той же валюте, что и цены (тенге за тенге). */
    val showCardLine: Boolean get() = !(source == Source.KZT && selected == Card.KZT)

    /** Текст активной строки: набираемое число или итог выражения «22 + 15,5». */
    val activeText: String
        get() = when {
            expression.isEmpty -> "0"
            expression.isCompound -> Numbers.money(expression.value)
            else -> expression.currentTerm()
        }
}

class MainViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = repository.load()
            _state.value = MainUiState(
                loaded = true,
                selected = stored.selected,
                rates = stored.rates,
                source = stored.source,
                active = stored.active,
                expression = stored.expression,
            )
            // Сохраняем не чаще раза в 300 мс: collectLatest отменяет отложенную запись при новом нажатии.
            _state.collectLatest { s ->
                delay(SAVE_DELAY_MS)
                repository.save(StoredState(s.selected, s.rates, s.source, s.active, s.expression))
            }
        }
    }

    fun press(key: Key) = _state.update { it.copy(expression = it.expression.press(key)) }

    fun selectCard(card: Card) = _state.update { s ->
        when {
            s.selected == card -> s
            // Сумма в валюте карты не должна прыгнуть: сначала переводим её в валюту цен.
            s.active == Line.CARD -> s.copy(selected = card, active = Line.SOURCE, expression = Expression.of(s.amounts.source))
            else -> s.copy(selected = card)
        }
    }

    /** Переключить валюту цен; сумма в рублях сохраняется и пересчитывается в новую валюту. */
    fun setSource(source: Source) = _state.update { s ->
        if (s.source == source) return@update s
        val rub = s.amounts.rub
        val next = s.copy(source = source, active = Line.SOURCE)
        val amount = Converter.toSource(rub, Line.RUB, next.selected, source, next.rates)
        next.copy(expression = Expression.of(amount))
    }

    fun setActive(line: Line) = _state.update { s ->
        if (s.active == line) s else s.copy(active = line, expression = Expression.of(s.amounts.of(line)))
    }

    /** Быстрая сумма в валюте цен: после «+» подставляется в выражение, иначе заменяет его. */
    fun putAmount(amount: BigDecimal) = _state.update { s ->
        if (s.active == Line.SOURCE) s.copy(expression = s.expression.putAmount(amount))
        else s.copy(active = Line.SOURCE, expression = Expression.of(amount))
    }

    /** Цены из камеры: одна — заменяет сумму, несколько — становятся выражением «a + b + c». */
    fun putItems(items: List<BigDecimal>) = _state.update { s ->
        val expression = when (items.size) {
            0 -> s.expression
            1 -> Expression.of(items.single())
            else -> Expression(items.map { Numbers.plain(it) })
        }
        s.copy(active = Line.SOURCE, expression = expression)
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
        private const val SAVE_DELAY_MS = 300L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                MainViewModel(SettingsRepository(app.settingsDataStore))
            }
        }

        /** Быстрые суммы; в дирхамах 22 — проезд в метро. */
        fun chips(source: Source): List<BigDecimal> = when (source) {
            Source.AED -> listOf(5, 10, 22, 50, 100, 200, 500)
            Source.KZT -> listOf(500, 1000, 2000, 5000, 10000, 20000, 50000)
        }.map { BigDecimal(it) }

        fun cheat(source: Source): List<BigDecimal> = when (source) {
            Source.AED -> listOf(1, 5, 10, 20, 50, 100, 200, 500, 1000)
            Source.KZT -> listOf(100, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000)
        }.map { BigDecimal(it) }

        /** Курс для подписи: «22,45 ₽ за AED», а тенге — за 100 ₸, иначе число слишком мелкое. */
        fun unitLabel(source: Source): BigDecimal = when (source) {
            Source.AED -> BigDecimal.ONE
            Source.KZT -> BigDecimal(100)
        }
    }
}
