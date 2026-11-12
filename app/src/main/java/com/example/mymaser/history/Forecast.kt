package com.example.mymaser.history

import java.time.YearMonth
import java.time.ZonedDateTime

data class YearEndForecast(
    /** Expected maser owed on December 31; zero or below means it will all be given. */
    val balance: Double,
    /** What to give each remaining month, including this one, to finish at zero. */
    val monthlyToClear: Double
)

/**
 * Projects the maser balance to the end of the year from recurring sources: each expected
 * income occurrence adds its maser share, each expected recurring donation subtracts its amount.
 * [history] must already be in one currency. Null when no income recurs, since there is nothing
 * meaningful to project.
 */
fun forecastYearEnd(history: List<History>, currentBalance: Double, now: ZonedDateTime): YearEndForecast? {
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
            val amount = suggestedAmount(entries, latest.name, isDonation, now) ?: return@forEach
            val dates = entries.map { it.localDate(now.zone) }
            if (!isDonation) hasRecurringIncome = true

            var month = currentMonth
            while (!month.isAfter(december)) {
                val expected = month.atDayClamped(recurrence.typicalDay)
                if (!dates.hasEntryNear(expected) && !expected.isBefore(today)) {
                    change += maserDelta(amount, isDonation, latest.maserPercentage)
                }
                month = month.plusMonths(1)
            }
        }

    if (!hasRecurringIncome) return null
    val balance = currentBalance + change
    val monthsLeft = 12 - today.monthValue + 1
    return YearEndForecast(balance, maxOf(balance, 0.0) / monthsLeft)
}
