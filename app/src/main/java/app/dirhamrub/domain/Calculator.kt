package app.dirhamrub.domain

import java.math.BigDecimal

/** Строка «линии метро»: валюта трат, итоговая валюта, валюта карты. */
enum class Line { SPEND, HOME, CARD }

/** Клавиши своей клавиатуры калькулятора. */
sealed interface Key {
    data class Digit(val digit: Int) : Key
    data object Comma : Key
    data object Backspace : Key
    data object Clear : Key
    data object Plus : Key
    data object Equals : Key
}

/**
 * Выражение калькулятора: сумма слагаемых «22 + 15,5 + 8».
 * Слагаемые хранятся как набранный текст с запятой («15,» допустимо во время набора).
 * [fresh] — значение подставлено извне (чип, «=», смена строки): следующая цифра начинает число заново.
 */
data class Expression(
    val terms: List<String> = listOf(""),
    val fresh: Boolean = false,
) {
    val isCompound: Boolean get() = terms.size > 1
    val isEmpty: Boolean get() = terms.all { it.isEmpty() }

    val value: BigDecimal
        get() = terms.fold(BigDecimal.ZERO) { acc, term -> acc.add(Numbers.parse(term) ?: BigDecimal.ZERO, MC) }

    /** «22 + 15,5 + 8», при наборе следующего слагаемого — «22 + ». */
    fun display(): String = terms.joinToString(" + ") { Numbers.groupInput(it) }

    /** Последнее (редактируемое) слагаемое в виде для показа. */
    fun currentTerm(): String = Numbers.groupInput(terms.last())

    fun serialize(): String = terms.joinToString("+")

    fun press(key: Key): Expression = when (key) {
        is Key.Digit -> digit(key.digit)
        Key.Comma -> comma()
        Key.Backspace -> backspace()
        Key.Clear -> Expression()
        Key.Plus -> plus()
        Key.Equals -> equals()
    }

    /** Подставить сумму в последнее слагаемое, если оно пустое (после «+»), иначе заменить выражение. */
    fun putAmount(amount: BigDecimal): Expression =
        if (!fresh && isCompound && terms.last().isEmpty()) {
            copy(terms = terms.dropLast(1) + Numbers.plain(amount))
        } else {
            of(amount)
        }

    private fun digit(d: Int): Expression {
        require(d in 0..9)
        if (fresh) return Expression(listOf(d.toString()))
        val last = terms.last()
        val comma = last.indexOf(',')
        val next = when {
            comma >= 0 && last.length - comma - 1 >= MAX_FRACTION -> return this
            comma < 0 && last.length >= MAX_INT_DIGITS -> return this
            last == "0" -> d.toString()
            else -> last + d
        }
        return replaceLast(next)
    }

    private fun comma(): Expression {
        if (fresh) return Expression(listOf("0,"))
        val last = terms.last()
        return when {
            last.contains(',') -> this
            last.isEmpty() -> replaceLast("0,")
            else -> replaceLast("$last,")
        }
    }

    private fun backspace(): Expression {
        val last = terms.last()
        return when {
            last.isEmpty() && terms.size > 1 -> Expression(terms.dropLast(1))
            last.isEmpty() -> Expression()
            else -> replaceLast(last.dropLast(1))
        }
    }

    private fun plus(): Expression {
        val last = terms.last()
        if (last.isEmpty() || terms.size >= MAX_TERMS) return copy(fresh = false)
        return Expression(terms + "")
    }

    private fun equals(): Expression = if (isEmpty) Expression() else of(value)

    private fun replaceLast(text: String) = Expression(terms.dropLast(1) + text)

    companion object {
        const val MAX_INT_DIGITS = 9
        const val MAX_FRACTION = 2
        const val MAX_TERMS = 30

        fun of(value: BigDecimal): Expression =
            if (value.signum() == 0) Expression() else Expression(listOf(Numbers.plain(value)), fresh = true)

        /** Обратная операция к [serialize]; всё непонятное отбрасывается. */
        fun parse(stored: String?): Expression {
            if (stored.isNullOrEmpty()) return Expression()
            val terms = stored.split('+').map { term ->
                term.filter { it.isDigit() || it == ',' }
            }
            val valid = terms.all { it.isEmpty() || Numbers.parse(it) != null }
            return if (valid && terms.isNotEmpty()) Expression(terms) else Expression()
        }
    }
}
