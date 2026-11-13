package com.example.mymaser.history

import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlookTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 11, 10, 15, 0, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun income(name: String, time: Long, amount: Float, percentage: Float = 10F) =
        History(timeStamp = time, name = name, amount = amount, maserPercentage = percentage)

    private fun donation(name: String, time: Long, amount: Float, taxCredit: Boolean = false, taxOnly: Boolean = false) =
        History(timeStamp = time, name = name, amount = amount, isDonation = true, taxCredit = taxCredit, isTaxOnly = taxOnly)

    private val salary = (6..10).map { income("Salary", at(2026, it, 20), 10000F) }
    private val pledge = (6..10).map { donation("Pledge", at(2026, it, 25), 300F, taxCredit = true) }

    @Test
    fun `forecast months run to December and end at the balance`() {
        val forecast = forecastYearEnd(salary + pledge, 500.0, now)!!
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12)), forecast.months.map { it.month })
        assertEquals(listOf(1200.0, 1900.0), forecast.months.map { it.balance })
        assertEquals(forecast.balance, forecast.months.last().balance, 0.001)
    }

    @Test
    fun `forecast sources list remaining and skipped occurrences`() {
        val skipped = setOf(predictionKey("Pledge", true, YearMonth.of(2026, 12)))
        val sources = forecastYearEnd(salary + pledge, 0.0, now, skipped)!!.sources.associateBy { it.name }
        assertEquals(2, sources.getValue("Salary").remaining)
        assertEquals(1000.0, sources.getValue("Salary").delta, 0.001)
        val pledgeSource = sources.getValue("Pledge")
        assertEquals(1, pledgeSource.remaining)
        assertEquals(1, pledgeSource.skipped)
        assertEquals(-300.0, pledgeSource.delta, 0.001)
        assertEquals(-300.0, pledgeSource.total, 0.001)
        assertEquals(2000.0, sources.getValue("Salary").total, 0.001)
        assertTrue(pledgeSource.taxCredit)
    }

    @Test
    fun `pace compares maser given with maser owed at each income's percentage`() {
        val history = listOf(
            income("Salary", at(2026, 1, 1), 10000F, percentage = 10F),
            income("Bonus", at(2026, 2, 1), 10000F, percentage = 20F),
            donation("Fund", at(2026, 3, 1), 1000F),
            donation("Receipt", at(2026, 3, 2), 500F, taxOnly = true),
            income("Old", at(2025, 12, 1), 50000F)
        )
        val pace = yearPace(history, now)!!
        assertEquals(20000.0, pace.income, 0.001)
        assertEquals(0.05, pace.givenShare, 0.0001)
        assertEquals(0.15, pace.targetShare, 0.0001)
    }

    @Test
    fun `no pace without income this year`() {
        assertNull(yearPace(listOf(donation("Fund", at(2026, 3, 1), 100F)), now))
    }

    @Test
    fun `tax credit is 35 percent of marked donations once past the minimum`() {
        val history = listOf(
            income("Salary", at(2026, 1, 1), 100000F),
            donation("Fund", at(2026, 3, 1), 1000F, taxCredit = true),
            donation("Other", at(2026, 3, 1), 5000F)
        )
        val year = taxYearCredit(history, 2026, now)
        assertEquals(1000.0, year.eligible, 0.001)
        assertEquals(350.0, year.credit, 0.001)
        assertEquals(0.0, year.overCap, 0.001)
    }

    @Test
    fun `nothing is credited at or below the yearly minimum`() {
        val year = taxYearCredit(listOf(donation("Fund", at(2026, 3, 1), 207F, taxCredit = true)), 2026, now)
        assertFalse(year.reachedMinimum)
        assertEquals(0.0, year.credit, 0.001)
    }

    @Test
    fun `donations above 30 percent of logged income get no credit`() {
        val history = listOf(
            income("Salary", at(2026, 1, 1), 10000F),
            donation("Fund", at(2026, 3, 1), 4000F, taxCredit = true)
        )
        val year = taxYearCredit(history, 2026, now)
        assertEquals(1000.0, year.overCap, 0.001)
        assertEquals(3000.0 * TAX_CREDIT_RATE, year.credit, 0.001)
    }

    @Test
    fun `tax years cover this year and six back, and tax-only alone doesn't count`() {
        val history = listOf(
            donation("Fund", at(2021, 5, 1), 1000F, taxCredit = true),
            donation("Receipt", at(2026, 5, 1), 1000F, taxOnly = true)
        )
        val years = taxCreditYears(history, now)
        assertEquals((2026 downTo 2020).toList(), years.map { it.year })
        assertEquals(1000.0, years.single { it.year == 2021 }.eligible, 0.001)
        assertEquals(0.0, years.first().eligible, 0.001)
        assertEquals(190.0, years.last().minimum, 0.001)
    }

    @Test
    fun `projection adds the recurring tax-credit donations still expected`() {
        val history = salary + pledge
        val projected = projectedTaxCredit(history, forecastYearEnd(history, 0.0, now), now)
        assertEquals(5 * 300.0 + 2 * 300.0, projected.eligible, 0.001)
        val withoutIncome = forecastYearEnd(pledge, 0.0, now, requireRecurringIncome = false)
        assertEquals(projected.eligible, projectedTaxCredit(pledge, withoutIncome, now).eligible, 0.001)
        assertNull(forecastYearEnd(pledge, 0.0, now))
    }

    @Test
    fun `overview totals, compares and ranks names`() {
        val history = listOf(
            income("Salary", at(2026, 3, 1), 10000F),
            donation("Fund", at(2026, 3, 2), 600F),
            donation("fund ", at(2026, 4, 2), 400F),
            donation("Shul", at(2026, 4, 3), 500F, taxOnly = true),
            income("Salary", at(2025, 3, 1), 8000F)
        )
        val overview = computeOverview(history, SummaryPeriod.ThisYear, now)
        assertEquals(10000.0, overview.current.income, 0.001)
        assertEquals(1500.0, overview.current.donations, 0.001)
        assertEquals(0.1, overview.current.givenShare!!, 0.0001)
        assertEquals(0.0, overview.current.maserChange, 0.001)
        val previous = overview.previous!!
        assertEquals(8000.0, previous.income, 0.001)
        assertEquals(0.25, relativeChange(overview.current.income, previous.income)!!, 0.0001)
        assertEquals(11, overview.monthly.size)
        assertEquals(600.0, overview.monthly[2].donations, 0.001)
        assertEquals(900.0, overview.monthly[3].donations, 0.001)
        val top = overview.topDonations.first()
        assertEquals("fund", top.name)
        assertEquals(1000.0 / 1500.0, top.share, 0.0001)
        assertEquals(overview.current, periodTotals(history, SummaryPeriod.ThisYear, now))
    }
}
