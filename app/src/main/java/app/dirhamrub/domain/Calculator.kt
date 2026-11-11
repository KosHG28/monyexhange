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
    data object Minus : Key
    data object Times : Key
    data object Equals : Key
}

/** Знак между числами: «+», «−» или «×» (умножение выполняется раньше сложения и вычитания). */
enum class Op(val symbol: Char, val stored: Char) {
    PLUS('+', '+'),
    MINUS('−', '-'),
    TIMES('×', '*'),
}

/**
 * Выражение калькулятора: «3 × 22 + 15,5 + 8» — сумма слагаемых, слагаемое может быть произведением.
 * Числа хранятся как набранный текст с запятой («15,» допустимо во время набора), [ops] — знаки между ними
 * (их на один меньше, чем чисел).
 * [fresh] — значение подставлено извне (чип, «=», смена строки): следующая цифра начинает число заново.
 */
data class Expression(
    val terms: List<String> = listOf(""),
    val fresh: Boolean = false,
    val ops: List<Op> = List(maxOf(0, terms.size - 1)) { Op.PLUS },
) {
    val isCompound: Boolean get() = terms.size > 1
    val isEmpty: Boolean get() = terms.all { it.isEmpty() }

    /**
     * Значение: сначала умножение, потом сложение и вычитание. Недописанный хвост («3 × ») не считается —
     * пока набирают второй множитель, видно 3, а не 0.
     */
    val value: BigDecimal
        get() {
            var count = terms.size
            if (count > 1 && terms.last().isEmpty()) count--
            var sum = BigDecimal.ZERO
            var product = number(0)
            var minus = false
            for (i in 1 until count) {
                if (ops[i - 1] == Op.TIMES) {
                    product = product.multiply(number(i), MC)
                } else {
                    sum = if (minus) sum.subtract(product, MC) else sum.add(product, MC)
                    minus = ops[i - 1] == Op.MINUS
                    product = number(i)
                }
            }
            return if (minus) sum.subtract(product, MC) else sum.add(product, MC)
        }

    private fun number(i: Int): BigDecimal = Numbers.parse(terms[i]) ?: BigDecimal.ZERO

    /** «3 × 22 + 15,5», при наборе следующего числа — «3 × 22 + ». */
    fun display(): String = buildString {
        terms.forEachIndexed { i, term ->
            if (i > 0) append(' ').append(ops[i - 1].symbol).append(' ')
            append(Numbers.groupInput(term))
        }
    }

    /** Последнее (редактируемое) число в виде для показа. */
    fun currentTerm(): String = Numbers.groupInput(terms.last())

    /** «3*22+15,5-2»: умножение хранится звёздочкой, вычитание — дефисом; старые сохранения «22+15,5» читаются как раньше. */
    fun serialize(): String = buildString {
        terms.forEachIndexed { i, term ->
            if (i > 0) append(ops[i - 1].stored)
            append(term)
        }
    }

    fun press(key: Key): Expression = when (key) {
        is Key.Digit -> digit(key.digit)
        Key.Comma -> comma()
        Key.Backspace -> backspace()
        Key.Clear -> Expression()
        Key.Plus -> operator(Op.PLUS)
        Key.Minus -> operator(Op.MINUS)
        Key.Times -> operator(Op.TIMES)
        Key.Equals -> equals()
    }

    /** Подставить сумму в последнее число, если оно пустое (после «+» или «×»), иначе заменить выражение. */
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
            last.isEmpty() && terms.size > 1 -> Expression(terms.dropLast(1), ops = ops.dropLast(1))
            last.isEmpty() -> Expression()
            else -> replaceLast(last.dropLast(1))
        }
    }

    /** «+», «−» или «×». Сразу после знака — заменяет его («3 + ×» → «3 × »); на пустом месте — ничего. */
    private fun operator(op: Op): Expression {
        val last = terms.last()
        if (last.isEmpty()) {
            return if (terms.size > 1) copy(ops = ops.dropLast(1) + op, fresh = false) else copy(fresh = false)
        }
        if (terms.size >= MAX_TERMS) return copy(fresh = false)
        return Expression(terms + "", ops = ops + op)
    }

    private fun equals(): Expression = if (isEmpty) Expression() else of(value)

    private fun replaceLast(text: String) = Expression(terms.dropLast(1) + text, ops = ops)

    companion object {
        const val MAX_INT_DIGITS = 9
        const val MAX_FRACTION = 2
        const val MAX_TERMS = 30

        fun of(value: BigDecimal): Expression =
            if (value.signum() == 0) Expression() else Expression(listOf(Numbers.plain(value)), fresh = true)

        /** Обратная операция к [serialize]; всё непонятное отбрасывается. */
        fun parse(stored: String?): Expression {
            if (stored.isNullOrEmpty()) return Expression()
            val terms = mutableListOf(StringBuilder())
            val ops = mutableListOf<Op>()
            for (ch in stored) {
                val op = Op.entries.firstOrNull { it.stored == ch }
                when {
                    op != null -> {
                        ops += op
                        terms += StringBuilder()
                    }
                    ch.isDigit() || ch == ',' -> terms.last().append(ch)
                }
            }
            val texts = terms.map { it.toString() }
            val valid = texts.all { it.isEmpty() || Numbers.parse(it) != null }
            return if (valid) Expression(texts, ops = ops) else Expression()
        }
    }
}
