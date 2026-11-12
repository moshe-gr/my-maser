package com.example.mymaser.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE

data class TimeRange(val start: Long, val endExclusive: Long) {
    operator fun contains(time: Long) = time in start..<endExclusive
}

internal fun LocalDate.startMillis(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()

fun monthRange(now: ZonedDateTime): TimeRange {
    val first = now.toLocalDate().withDayOfMonth(1)
    return TimeRange(first.startMillis(now.zone), first.plusMonths(1).startMillis(now.zone))
}

fun yearRange(now: ZonedDateTime): TimeRange {
    val first = now.toLocalDate().withDayOfYear(1)
    return TimeRange(first.startMillis(now.zone), first.plusYears(1).startMillis(now.zone))
}

fun previousYearRange(now: ZonedDateTime): TimeRange {
    val first = now.toLocalDate().withDayOfYear(1)
    return TimeRange(first.minusYears(1).startMillis(now.zone), first.startMillis(now.zone))
}

fun maserDelta(
    amount: Float,
    isDonation: Boolean,
    percentage: Float = DEFAULT_MASER_PERCENTAGE
): Float = if (isDonation) -amount else amount * (percentage / 100f)

val History.maserDelta: Float get() = maserDelta(amount, isDonation, maserPercentage)

enum class TypeFilter { All, Income, Donations }

enum class TimeFilter { AllTime, Today, ThisWeek, ThisMonth, ThisYear, LastYear, SpecificMonth, SpecificYear }

data class HistoryQuery(
    val text: String = "",
    val type: TypeFilter = TypeFilter.All,
    val time: TimeFilter = TimeFilter.AllTime,
    val month: YearMonth? = null,
    val year: Int? = null
) {
    val isActive: Boolean
        get() = text.isNotBlank() || type != TypeFilter.All || time != TimeFilter.AllTime
}

fun filterHistory(
    history: List<History>,
    query: HistoryQuery,
    now: ZonedDateTime,
    firstDayOfWeek: DayOfWeek
): List<History> {
    val today = now.toLocalDate()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val yearStart = today.withDayOfYear(1)
    val text = query.text.trim()
    return history.filter { entry ->
        val date = entry.localDate(now.zone)
        val matchesType = when (query.type) {
            TypeFilter.All -> true
            TypeFilter.Income -> !entry.isDonation
            TypeFilter.Donations -> entry.isDonation
        }
        val matchesTime = when (query.time) {
            TimeFilter.AllTime -> true
            TimeFilter.Today -> date == today
            TimeFilter.ThisWeek -> !date.isBefore(weekStart)
            TimeFilter.ThisMonth -> YearMonth.from(date) == YearMonth.from(today)
            TimeFilter.ThisYear -> date.year == today.year
            TimeFilter.LastYear -> date.year == yearStart.year - 1
            TimeFilter.SpecificMonth -> query.month == null || YearMonth.from(date) == query.month
            TimeFilter.SpecificYear -> query.year == null || date.year == query.year
        }
        matchesType && matchesTime &&
            (text.isEmpty() || entry.name.contains(text, ignoreCase = true))
    }
}

data class HomeStats(
    val donatedThisMonth: Double = 0.0,
    val donatedThisYear: Double = 0.0,
    val donatedTotal: Double = 0.0,
    val lastActionTime: Long? = null
)

fun computeHomeStats(history: List<History>, now: ZonedDateTime): HomeStats {
    val donations = history.filter { it.isDonation }
    val month = monthRange(now)
    val year = yearRange(now)
    return HomeStats(
        donatedThisMonth = donations.filter { it.timeStamp in month }.sumOf { it.amount.toDouble() },
        donatedThisYear = donations.filter { it.timeStamp in year }.sumOf { it.amount.toDouble() },
        donatedTotal = donations.sumOf { it.amount.toDouble() },
        lastActionTime = history.maxOfOrNull { it.timeStamp }
    )
}

sealed interface HistoryPeriod {
    data object Today : HistoryPeriod
    data object ThisWeek : HistoryPeriod
    data object ThisMonth : HistoryPeriod
    data class Month(val month: YearMonth) : HistoryPeriod
}

/** Keeps the order of [history], so newest-first input yields newest-first periods. */
fun groupByPeriod(
    history: List<History>,
    now: ZonedDateTime,
    firstDayOfWeek: DayOfWeek
): List<Pair<HistoryPeriod, List<History>>> {
    val today = now.toLocalDate()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val monthStart = today.withDayOfMonth(1)
    return history.groupBy { entry ->
        val date = entry.localDate(now.zone)
        when {
            date == today -> HistoryPeriod.Today
            !date.isBefore(weekStart) -> HistoryPeriod.ThisWeek
            !date.isBefore(monthStart) -> HistoryPeriod.ThisMonth
            else -> HistoryPeriod.Month(YearMonth.from(date))
        }
    }.toList()
}

fun formatAmountInput(amount: Float): String =
    if (amount == amount.toLong().toFloat()) amount.toLong().toString() else amount.toString()

private val AMOUNT_INPUT = Regex("^\\d{0,12}\\.?\\d{0,5}$")

/** Returns the normalized text when [input] is a valid partial amount, otherwise null. */
fun sanitizeAmountInput(input: String): String? =
    input.replace(',', '.').takeIf { AMOUNT_INPUT.matches(it) }
