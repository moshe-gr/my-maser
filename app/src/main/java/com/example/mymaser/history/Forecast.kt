package com.example.mymaser.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

data class YearEndForecast(
    /** Expected maser owed on December 31; zero or below means it will all be given. */
    val balance: Double,
    /**
     * What to give each remaining month, including this one, on top of the recurring donations
     * already in [balance], to finish at zero.
     */
    val monthlyToClear: Double
)

/**
 * Projects the maser balance to the end of the year from recurring sources: each expected
 * income occurrence adds its maser share, each expected recurring donation subtracts its amount.
 * This month's occurrence counts until it is logged or skipped, even once its usual day has
 * passed, so logging a late payment doesn't move the forecast. Occurrences whose
 * [Prediction.key] is in [dismissed] are left out. [history] must already be in one currency.
 * Null when no income recurs, since there is nothing meaningful to project.
 */
fun forecastYearEnd(
    history: List<History>,
    currentBalance: Double,
    now: ZonedDateTime,
    dismissed: Set<String> = emptySet()
): YearEndForecast? {
    val today = now.toLocalDate()
    val currentMonth = YearMonth.from(today)
    val december = YearMonth.of(today.year, 12)
    var change = 0.0
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

            var month = currentMonth
            while (!month.isAfter(december)) {
                val expected = month.atDayClamped(recurrence.typicalDay)
                val logged = dates.hasEntryNear(expected) ||
                    (month == currentMonth && dates.any { it.isLateFor(expected, month) })
                val skipped = predictionKey(latest.name, isDonation, month) in dismissed
                if (!logged && !skipped) change += delta
                month = month.plusMonths(1)
            }
        }

    if (!hasRecurringIncome) return null
    val balance = currentBalance + change
    val monthsLeft = 12 - today.monthValue + 1
    return YearEndForecast(balance, maxOf(balance, 0.0) / monthsLeft)
}

/** Logged after [expected] but still within its [month]. */
private fun LocalDate.isLateFor(expected: LocalDate, month: YearMonth) =
    !isBefore(expected) && YearMonth.from(this) == month
