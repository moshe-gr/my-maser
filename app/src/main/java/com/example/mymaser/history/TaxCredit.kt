package com.example.mymaser.history

import java.time.LocalDate
import java.time.ZonedDateTime

/** Share of eligible donations an individual gets back under section 46. */
const val TAX_CREDIT_RATE = 0.35

/** Donations above this share of the year's taxable income get no credit. */
const val TAX_CREDIT_INCOME_CAP = 0.30

/** How many tax years back a refund can still be claimed. */
const val TAX_CLAIM_YEARS = 6

/** The yearly total donations must pass before any credit is given, in shekels. */
private val MIN_DONATION_BY_YEAR = mapOf(
    2020 to 190.0,
    2021 to 190.0,
    2022 to 190.0,
    2023 to 200.0,
    2024 to 207.0,
    2025 to 207.0,
    2026 to 207.0
)

fun minimumTaxCreditDonation(year: Int): Double =
    MIN_DONATION_BY_YEAR[year]
        ?: MIN_DONATION_BY_YEAR.getValue(year.coerceIn(MIN_DONATION_BY_YEAR.keys.min(), MIN_DONATION_BY_YEAR.keys.max()))

/** One tax year's section 46 picture, from the entries the user logged. */
data class TaxYearCredit(
    val year: Int,
    /** Donations marked as counting for the tax credit. */
    val eligible: Double,
    /** All income logged in the year; the cap is estimated from it. */
    val income: Double,
    val minimum: Double = minimumTaxCreditDonation(year)
) {
    val reachedMinimum: Boolean get() = eligible > minimum

    /** The most that can earn credit, or null when no income is logged to estimate it from. */
    val cap: Double? get() = income.takeIf { it > 0 }?.let { it * TAX_CREDIT_INCOME_CAP }

    /** Eligible donations above [cap], which earn no credit. */
    val overCap: Double get() = cap?.let { maxOf(eligible - it, 0.0) } ?: 0.0

    val credit: Double
        get() = if (reachedMinimum) minOf(eligible, cap ?: eligible) * TAX_CREDIT_RATE else 0.0
}

/** [year]'s eligible donations and income. [history] must already be in one currency. */
fun taxYearCredit(history: List<History>, year: Int, now: ZonedDateTime, extraEligible: Double = 0.0): TaxYearCredit {
    val zone = now.zone
    val start = LocalDate.of(year, 1, 1)
    val range = TimeRange(start.startMillis(zone), start.plusYears(1).startMillis(zone))
    val inYear = history.filter { it.timeStamp in range }
    return TaxYearCredit(
        year = year,
        eligible = inYear.filter { it.isTaxCreditDonation }.sumOf { it.amount.toDouble() } + extraEligible,
        income = inYear.filter { !it.isDonation }.sumOf { it.amount.toDouble() }
    )
}

/** This year and the [TAX_CLAIM_YEARS] before it that can still be claimed, newest first. */
fun taxCreditYears(history: List<History>, now: ZonedDateTime): List<TaxYearCredit> =
    (0..TAX_CLAIM_YEARS).map { back -> taxYearCredit(history, now.year - back, now) }

/** This year's credit if the recurring tax-credit donations still expected in [forecast] are given. */
fun projectedTaxCredit(history: List<History>, forecast: YearEndForecast?, now: ZonedDateTime): TaxYearCredit {
    val upcoming = forecast?.sources.orEmpty()
        .filter { it.isDonation && it.taxCredit }
        .sumOf { it.amount * it.remaining }
    return taxYearCredit(history, now.year, now, extraEligible = upcoming)
}
