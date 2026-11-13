package com.example.mymaser.history

import java.time.YearMonth
import java.time.ZonedDateTime

data class MonthPair(val month: YearMonth, val income: Double, val donations: Double)

/** A name's part of everything of its type in a period. */
data class NameShare(
    val name: String,
    val total: Double,
    val share: Double,
    /** The newest entry under this name in the period, to open its summary. */
    val latestId: Int
)

data class PeriodTotals(
    val income: Double,
    val donations: Double,
    /** Donations that reduce the maser balance; tax-only ones are left out. */
    val maserDonations: Double,
    /** Net change to the maser balance: maser added by income less maser donations. */
    val maserChange: Double
) {
    /** Share of income given as maser, or null without income. */
    val givenShare: Double? get() = income.takeIf { it > 0 }?.let { maserDonations / it }
}

data class Overview(
    val current: PeriodTotals,
    /** The comparable earlier period, or null for all time. */
    val previous: PeriodTotals?,
    val monthly: List<MonthPair>,
    val topDonations: List<NameShare>,
    val topIncome: List<NameShare>
)

/** Relative change from [previous] to [current], or null when there is nothing to compare with. */
fun relativeChange(current: Double, previous: Double?): Double? =
    previous?.takeIf { it > 0 }?.let { (current - it) / it }

private const val TOP_NAMES = 5
private const val CHART_MONTHS = 12

private fun inPeriod(history: List<History>, period: SummaryPeriod, now: ZonedDateTime) =
    periodRange(period, now)?.let { range -> history.filter { it.timeStamp in range } } ?: history

/** Just the totals for [period]. [history] must already be in one currency. */
fun periodTotals(history: List<History>, period: SummaryPeriod, now: ZonedDateTime): PeriodTotals =
    totals(inPeriod(history, period, now))

/** Totals, monthly bars and top names for [period]. [history] must already be in one currency. */
fun computeOverview(history: List<History>, period: SummaryPeriod, now: ZonedDateTime): Overview {
    val inPeriod = inPeriod(history, period, now)
    val previous = previousPeriodRange(period, now)?.let { range -> totals(history.filter { it.timeStamp in range }) }
    return Overview(
        current = totals(inPeriod),
        previous = previous,
        monthly = chartMonths(period, now).map { month ->
            val inMonth = history.filter { YearMonth.from(it.localDate(now.zone)) == month }
            MonthPair(
                month,
                inMonth.filter { !it.isDonation }.sumOf { it.amount.toDouble() },
                inMonth.filter { it.isDonation }.sumOf { it.amount.toDouble() }
            )
        },
        topDonations = topNames(inPeriod.filter { it.isDonation }),
        topIncome = topNames(inPeriod.filter { !it.isDonation })
    )
}

private fun totals(entries: List<History>) = PeriodTotals(
    income = entries.filter { !it.isDonation }.sumOf { it.amount.toDouble() },
    donations = entries.filter { it.isDonation }.sumOf { it.amount.toDouble() },
    maserDonations = entries.filter { it.isMaserDonation }.sumOf { it.amount.toDouble() },
    maserChange = entries.sumOf { it.maserDelta.toDouble() }
)

/** The months a year-bound period covers; otherwise the last twelve. */
private fun chartMonths(period: SummaryPeriod, now: ZonedDateTime): List<YearMonth> {
    val current = YearMonth.from(now)
    return when (period) {
        SummaryPeriod.ThisYear -> (1..current.monthValue).map { YearMonth.of(current.year, it) }
        SummaryPeriod.LastYear -> (1..12).map { YearMonth.of(current.year - 1, it) }
        else -> (CHART_MONTHS - 1 downTo 0).map { current.minusMonths(it.toLong()) }
    }
}

private fun topNames(entries: List<History>): List<NameShare> {
    val all = entries.sumOf { it.amount.toDouble() }
    if (all <= 0) return emptyList()
    return entries.groupBy { nameKey(it.name) }
        .filterKeys { it.isNotEmpty() }
        .values
        .map { group ->
            val total = group.sumOf { it.amount.toDouble() }
            val latest = group.maxBy { it.timeStamp }
            NameShare(latest.name.trim(), total, total / all, latest.id)
        }
        .sortedByDescending { it.total }
        .take(TOP_NAMES)
}
