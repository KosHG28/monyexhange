package app.dirhamrub.domain.wallet

import app.dirhamrub.domain.Expression
import app.dirhamrub.domain.Line
import java.math.BigDecimal

data class InputSnapshot(val currency: Currency, val line: Line, val expression: Expression, val homeAmount: BigDecimal?) {
    fun encode(): String = listOf(currency.code, line.name, expression.serialize(),
        if (expression.fresh) "1" else "0", homeAmount?.toPlainString().orEmpty()).joinToString("|")

    companion object {
        fun decode(stored: String?): InputSnapshot? {
            val parts = stored?.split('|') ?: return null
            if (parts.size != 5) return null
            val currency = Currency.byCode(parts[0]) ?: return null
            val line = Line.entries.firstOrNull { it.name == parts[1] && it != Line.CARD } ?: return null
            val expression = Expression.parse(parts[2]).copy(fresh = parts[3] == "1")
            if (expression.isEmpty) return null
            return InputSnapshot(currency, line, expression, parts[4].toBigDecimalOrNull())
        }
    }
}
