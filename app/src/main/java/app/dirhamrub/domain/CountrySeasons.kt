package app.dirhamrub.domain

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

enum class SeasonalEffect { FLOWER, LEAF, SNOW, LIGHT, WATER, FRUIT, CONFETTI }

/** Календарь офлайн. Сезон — ориентир по стране; точность по регионам поясняет заметка. */
data class CountrySeason(
    val iso: String, val symbol: String, val effect: SeasonalEffect,
    val start: MonthDay? = null, val end: MonthDay? = null,
    val dates: List<LocalDate> = emptyList(), val bali: Boolean = false,
    val dateRange: ClosedRange<LocalDate>? = null,
) {
    fun active(date: LocalDate): Boolean {
        dateRange?.let { return date in it }
        if (bali) return Math.floorMod(ChronoUnit.DAYS.between(LocalDate.of(2025, 4, 23), date), 210L) <= 2L
        if (dates.isNotEmpty()) return dates.any { kotlin.math.abs(ChronoUnit.DAYS.between(it, date)) <= 3 }
        val from = start ?: return false
        val to = end ?: return false
        val day = MonthDay.from(date)
        return if (from <= to) day >= from && day <= to else day >= from || day <= to
    }
}

object CountrySeasons {
    private val newYears = listOf("2026-02-17", "2027-02-06", "2028-01-26", "2029-02-13", "2030-02-03").map(LocalDate::parse)
    private val moons = listOf("2026-09-25", "2027-09-15", "2028-10-03", "2029-09-22", "2030-09-12").map(LocalDate::parse)
    val all: List<CountrySeason> = listOf(
        CountrySeason("RU", "❄", SeasonalEffect.SNOW, MonthDay.of(12, 1), MonthDay.of(2, 29)),
        CountrySeason("BY", "🍂", SeasonalEffect.LEAF, MonthDay.of(9, 1), MonthDay.of(10, 31)),
        CountrySeason("KZ", "🌷", SeasonalEffect.FLOWER, MonthDay.of(3, 20), MonthDay.of(3, 25)),
        CountrySeason("KG", "☀", SeasonalEffect.LIGHT, MonthDay.of(3, 20), MonthDay.of(3, 23)),
        CountrySeason("TJ", "🌸", SeasonalEffect.FLOWER, MonthDay.of(3, 20), MonthDay.of(3, 25)),
        CountrySeason("UZ", "🌱", SeasonalEffect.LEAF, MonthDay.of(3, 20), MonthDay.of(3, 25)),
        CountrySeason("TM", "🌼", SeasonalEffect.FLOWER, MonthDay.of(3, 20), MonthDay.of(3, 23)),
        CountrySeason("AZ", "🌱", SeasonalEffect.LEAF, MonthDay.of(3, 20), MonthDay.of(3, 26)),
        CountrySeason("AM", "🍑", SeasonalEffect.FRUIT, MonthDay.of(6, 1), MonthDay.of(7, 31)),
        CountrySeason("MD", "🧵", SeasonalEffect.LIGHT, MonthDay.of(3, 1), MonthDay.of(3, 31)),
        CountrySeason("GE", "🍇", SeasonalEffect.FRUIT, MonthDay.of(9, 1), MonthDay.of(10, 31)),
        CountrySeason("TR", "🌷", SeasonalEffect.FLOWER, MonthDay.of(4, 1), MonthDay.of(4, 30)),
        CountrySeason("AE", "☀", SeasonalEffect.LIGHT, MonthDay.of(11, 1), MonthDay.of(3, 31)),
        CountrySeason("EG", "🌼", SeasonalEffect.FLOWER, dates = listOf(LocalDate.parse("2026-04-13"))),
        CountrySeason("OM", "🌿", SeasonalEffect.WATER, MonthDay.of(6, 21), MonthDay.of(9, 20)),
        CountrySeason("QA", "⛵", SeasonalEffect.WATER, MonthDay.of(11, 1), MonthDay.of(3, 31)),
        CountrySeason("TH", "🕯", SeasonalEffect.WATER, dates = listOf(LocalDate.parse("2026-11-24"))),
        CountrySeason("VN", "🌼", SeasonalEffect.FLOWER, dates = newYears),
        CountrySeason("ID", "🌿", SeasonalEffect.LEAF, bali = true),
        CountrySeason("MY", "🏮", SeasonalEffect.LIGHT, dates = newYears),
        CountrySeason("IN", "🎨", SeasonalEffect.CONFETTI, dates = listOf(LocalDate.parse("2026-03-04"))),
        CountrySeason("LK", "🏮", SeasonalEffect.LIGHT, dates = listOf(LocalDate.parse("2026-05-01"))),
        CountrySeason("MV", "🌊", SeasonalEffect.WATER, MonthDay.of(12, 1), MonthDay.of(4, 30)),
        CountrySeason("SC", "🐢", SeasonalEffect.WATER, MonthDay.of(10, 1), MonthDay.of(2, 29)),
        CountrySeason("CN", "🌕", SeasonalEffect.LIGHT, dates = moons),
        CountrySeason("JP", "🌸", SeasonalEffect.FLOWER, MonthDay.of(3, 1), MonthDay.of(5, 31)),
        CountrySeason("KR", "🌸", SeasonalEffect.FLOWER, MonthDay.of(3, 20), MonthDay.of(4, 30)),
        CountrySeason("FR", "🌸", SeasonalEffect.FLOWER, MonthDay.of(6, 15), MonthDay.of(7, 31)),
        CountrySeason("IT", "🎭", SeasonalEffect.CONFETTI, dateRange = LocalDate.parse("2026-01-31")..LocalDate.parse("2026-02-17")),
        CountrySeason("ES", "🌸", SeasonalEffect.FLOWER, MonthDay.of(2, 1), MonthDay.of(3, 31)),
        CountrySeason("GR", "🫒", SeasonalEffect.LEAF, MonthDay.of(10, 1), MonthDay.of(1, 31)),
        CountrySeason("CY", "🌸", SeasonalEffect.FLOWER, MonthDay.of(2, 1), MonthDay.of(3, 31)),
        CountrySeason("ME", "🌼", SeasonalEffect.FLOWER, MonthDay.of(2, 1), MonthDay.of(3, 31)),
        CountrySeason("RS", "🍇", SeasonalEffect.FRUIT, MonthDay.of(9, 1), MonthDay.of(10, 31)),
        CountrySeason("NL", "🌷", SeasonalEffect.FLOWER, MonthDay.of(3, 20), MonthDay.of(5, 15)),
        CountrySeason("CZ", "✨", SeasonalEffect.SNOW, MonthDay.of(12, 1), MonthDay.of(12, 31)),
        CountrySeason("CA", "🍁", SeasonalEffect.LEAF, MonthDay.of(9, 1), MonthDay.of(10, 31)),
    )
    fun current(iso: String?, date: LocalDate): CountrySeason? = all.firstOrNull { it.iso == iso && it.active(date) }
}
