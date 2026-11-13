package com.example.mymaser.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import kotlin.math.abs

/** The expected maser balance once [month]'s recurring entries are in. */
data class MonthProjection(val month: YearMonth, val balance: Double)

/** One recurring source's part in the forecast. */
data class SourceProjection(
    val name: String,
    val isDonation: Boolean,
    /** The usual amount of one occurrence. */
    val amount: Double,
    /** What one occurrence does to the maser balance: positive for income, negative for donations. */
    val delta: Double,
    /** Occurrences still expected this year, this month's included until it is logged or skipped. */
    val remaining: Int,
    /** Occurrences this year the user skipped. */
    val skipped: Int,
    val taxCredit: Boolean
) {
    /** What the occurrences still expected this year add to the year-end balance. */
    val total: Double get() = delta * remaining
}

data class YearEndForecast(
    /** Expected maser owed on December 31; zero or below means it will all be given. */
    val balance: Double,
    /**
     * What to give each remaining month, including this one, on top of the recurring donations
     * already in [balance], to finish at zero.
     */
    val monthlyToClear: Double,
    /** From this month to December. */
    val months: List<MonthProjection> = emptyList(),
    /** Sources still expected this year, largest effect first. */
    val sources: List<SourceProjection> = emptyList()
)

/**
 * Projects the maser balance to the end of the year from recurring sources: each expected
 * income occurrence adds its maser share, each expected recurring donation subtracts its amount.
 * This month's occurrence counts until it is logged or skipped, even once its usual day has
 * passed, so logging a late payment doesn't move the forecast. Occurrences whose
 * [Prediction.key] is in [dismissed] are left out. [history] must already be in one currency.
 * Null when no income recurs, since there is nothing meaningful to project, unless
 * [requireRecurringIncome] is false.
 */
fun forecastYearEnd(
    history: List<History>,
    currentBalance: Double,
    now: ZonedDateTime,
    dismissed: Set<String> = emptySet(),
    requireRecurringIncome: Boolean = true
): YearEndForecast? {
    val today = now.toLocalDate()
    val currentMonth = YearMonth.from(today)
    val december = YearMonth.of(today.year, 12)
    val changeByMonth = mutableMapOf<YearMonth, Double>()
    val sources = mutableListOf<SourceProjection>()
    var hasRecurringIncome = false

    history.bySource()
        .forEach { (group, entries) ->
            val (isDonation, _) = group
            val recurrence = detectRecurrence(entries, now) ?: return@forEach
            val latest = entries.maxBy { it.timeStamp }
            // Voting as of the usual day keeps one-offs logged on other days from setting the amount.
            val usualDay = now.with(currentMonth.atDayClamped(recurrence.typicalDay))
            val amount = suggestedAmount(entries, latest.name, isDonation, usualDay) ?: return@forEach
            val delta = maserDelta(amount, isDonation, latest.maserPercentage, latest.isTaxOnlyDonation)
            val dates = entries.map { it.localDate(now.zone) }
            if (!isDonation) hasRecurringIncome = true

            var remaining = 0
            var skippedCount = 0
            var month = currentMonth
            while (!month.isAfter(december)) {
                val expected = month.atDayClamped(recurrence.typicalDay)
                val logged = dates.hasEntryNear(expected) ||
                    (month == currentMonth && dates.any { it.isLateFor(expected, month) })
                val skipped = predictionKey(latest.name, isDonation, month) in dismissed
                when {
                    skipped -> skippedCount++
                    !logged -> {
                        remaining++
                        changeByMonth.merge(month, delta.toDouble(), Double::plus)
                    }
                }
                month = month.plusMonths(1)
            }
            if (remaining > 0 || skippedCount > 0) {
                sources += SourceProjection(
                    name = latest.name.trim(),
                    isDonation = isDonation,
                    amount = amount.toDouble(),
                    delta = delta.toDouble(),
                    remaining = remaining,
                    skipped = skippedCount,
                    taxCredit = latest.isTaxCreditDonation
                )
            }
        }

    if (requireRecurringIncome && !hasRecurringIncome) return null
    var running = currentBalance
    val months = generateSequence(currentMonth) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(december) }
        .map { month ->
            running += changeByMonth[month] ?: 0.0
            MonthProjection(month, running)
        }
        .toList()
    val balance = running
    val monthsLeft = 12 - today.monthValue + 1
    return YearEndForecast(
        balance = balance,
        monthlyToClear = maxOf(balance, 0.0) / monthsLeft,
        months = months,
        sources = sources.sortedByDescending { abs(it.total) }
    )
}

/** Logged after [expected] but still within its [month]. */
private fun LocalDate.isLateFor(expected: LocalDate, month: YearMonth) =
    !isBefore(expected) && YearMonth.from(this) == month
