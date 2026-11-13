package com.example.mymaser.history

import java.time.ZonedDateTime

/** How much of this year's income has been given, against the maser percentage it was logged at. */
data class YearPace(
    val income: Double,
    /** Donations that reduce the maser balance; tax-only ones are left out. */
    val given: Double,
    /** Maser owed on this year's income at each entry's own percentage. */
    val owed: Double
) {
    val givenShare: Double get() = given / income
    val targetShare: Double get() = owed / income
}

/** This calendar year's pace, or null when no income is logged yet. [history] must be in one currency. */
fun yearPace(history: List<History>, now: ZonedDateTime): YearPace? {
    val year = yearRange(now)
    val inYear = history.filter { it.timeStamp in year }
    val incomes = inYear.filter { !it.isDonation }
    val income = incomes.sumOf { it.amount.toDouble() }
    if (income <= 0) return null
    return YearPace(
        income = income,
        given = inYear.filter { it.isMaserDonation }.sumOf { it.amount.toDouble() },
        owed = incomes.sumOf { it.maserDelta.toDouble() }
    )
}
