package com.example.mymaser.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameAnalyticsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 11, 10, 15, 30, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun donation(name: String, time: Long, amount: Float) =
        History(timeStamp = time, name = name, amount = amount, isDonation = true)

    private fun income(name: String, time: Long, amount: Float, percentage: Float = 10F) =
        History(timeStamp = time, name = name, amount = amount, maserPercentage = percentage)

    private val charity = listOf(
        donation("Charity", at(2026, 11, 2), 100F),
        donation("charity ", at(2026, 6, 1), 300F),
        donation("Charity", at(2025, 11, 5), 50F),
        donation("Charity", at(2025, 3, 1), 150F),
        donation("Other", at(2026, 11, 3), 100F),
        income("Charity", at(2026, 11, 4), 9999F)
    )

    private fun analytics(period: SummaryPeriod, history: List<History> = charity) =
        computeNameAnalytics(history, history.first(), period, now)

    @Test
    fun `this year totals merge name spellings and ignore other types`() {
        val result = analytics(SummaryPeriod.ThisYear)
        assertEquals(400.0, result.total, 0.001)
        assertEquals(2, result.count)
        assertEquals(200.0, result.average, 0.001)
        assertEquals(300.0, result.highest!!, 0.001)
        assertEquals(100.0, result.lowest!!, 0.001)
        assertEquals(at(2026, 6, 1), result.firstTime)
        assertEquals(at(2026, 11, 2), result.lastTime)
    }

    @Test
    fun `this year compares with last year up to the same date`() {
        val result = analytics(SummaryPeriod.ThisYear)
        assertEquals(200.0, result.previousTotal!!, 0.001)
        assertEquals(1.0, result.change!!, 0.001)
    }

    @Test
    fun `share is against all entries of the same type`() {
        assertEquals(0.8, analytics(SummaryPeriod.ThisYear).shareOfType!!, 0.001)
    }

    @Test
    fun `this month and last year use calendar boundaries`() {
        assertEquals(100.0, analytics(SummaryPeriod.ThisMonth).total, 0.001)
        assertEquals(200.0, analytics(SummaryPeriod.LastYear).total, 0.001)
    }

    @Test
    fun `all time has no comparison`() {
        val result = analytics(SummaryPeriod.AllTime)
        assertEquals(600.0, result.total, 0.001)
        assertNull(result.previousTotal)
        assertNull(result.change)
    }

    @Test
    fun `no previous amount means no change figure`() {
        val history = listOf(donation("New", at(2026, 11, 1), 10F))
        assertNull(analytics(SummaryPeriod.ThisMonth, history).change)
    }

    @Test
    fun `an empty period has zero totals and no extremes`() {
        val history = listOf(donation("Old", at(2020, 1, 1), 10F))
        val result = analytics(SummaryPeriod.ThisYear, history)
        assertEquals(0, result.count)
        assertEquals(0.0, result.average, 0.001)
        assertNull(result.highest)
        assertNull(result.shareOfType)
    }

    @Test
    fun `last 12 months ends with the current month`() {
        val monthly = analytics(SummaryPeriod.ThisYear).monthly
        assertEquals(12, monthly.size)
        assertEquals(YearMonth.of(2025, 12), monthly.first().month)
        assertEquals(YearMonth.of(2026, 11), monthly.last().month)
        assertEquals(100.0, monthly.last().total, 0.001)
        assertEquals(300.0, monthly.first { it.month == YearMonth.of(2026, 6) }.total, 0.001)
    }

    @Test
    fun `last 12 months period compares with the 12 months before`() {
        val result = analytics(SummaryPeriod.Last12Months)
        assertEquals(400.0, result.total, 0.001)
        assertEquals(200.0, result.previousTotal!!, 0.001)
    }

    @Test
    fun `maser effect uses each entry's own percentage`() {
        val history = listOf(
            income("Salary", at(2026, 10, 10), 1000F, percentage = 10F),
            income("Salary", at(2026, 9, 10), 1000F, percentage = 20F)
        )
        assertEquals(300.0, analytics(SummaryPeriod.ThisYear, history).maserEffect, 0.001)
    }

    @Test
    fun `monthly entries on the same day are recurring`() {
        val history = (1..5).map { income("Salary", at(2026, 11 - it, 9 + it % 2), 1000F) }
        val recurrence = detectRecurrence(history, now)
        assertNotNull(recurrence)
        assertTrue(recurrence!!.typicalDay in 9..10)
        assertEquals(LocalDate.of(2026, 11, recurrence.typicalDay), recurrence.nextExpected)
    }

    @Test
    fun `once paid this month the next expected date is next month`() {
        val history = (0..3).map { income("Salary", at(2026, 11 - it, 1), 1000F) }
        assertEquals(LocalDate.of(2026, 12, 1), detectRecurrence(history, now)!!.nextExpected)
    }

    @Test
    fun `month-end payments wrap around and clamp to short months`() {
        val history = listOf(
            income("Rent", at(2026, 8, 31), 1F),
            income("Rent", at(2026, 9, 30), 1F),
            income("Rent", at(2026, 10, 1), 1F)
        )
        val recurrence = detectRecurrence(history, now)!!
        assertTrue(recurrence.typicalDay in listOf(30, 31, 1))
    }

    @Test
    fun `scattered or rare entries are not recurring`() {
        val scattered = listOf(
            income("Gig", at(2026, 10, 2), 1F),
            income("Gig", at(2026, 9, 15), 1F),
            income("Gig", at(2026, 8, 25), 1F)
        )
        assertNull(detectRecurrence(scattered, now))
        assertNull(detectRecurrence(scattered.take(2), now))
    }

    @Test
    fun `a source that stopped months ago is no longer recurring`() {
        val stopped = (1..4).map { income("Salary", at(2026, it, 10), 1000F) }
        assertNull(detectRecurrence(stopped, now))
    }

    @Test
    fun `one missed month still counts as recurring`() {
        val history = (7..9).map { income("Salary", at(2026, it, 10), 1000F) }
        assertNotNull(detectRecurrence(history, now))
    }

    @Test
    fun `csv has a header, oldest first rows and escaped names`() {
        val csv = entriesToCsv(
            listOf(
                donation("Fund \"A\", Inc", at(2026, 11, 2), 12.5F),
                income("=SUM(A1)", at(2026, 10, 1), 1000F, percentage = 20F)
            ),
            zone
        ).lines()
        assertEquals(
            "date,name,type,amount,currency,original_amount,original_currency," +
                "maser_percentage,maser_effect,tax_only",
            csv[0]
        )
        assertEquals(
            "2026-10-01 12:00,\"'=SUM(A1)\",income,1000.00,ILS,1000.00,ILS,20.00,200.00,false",
            csv[1]
        )
        assertEquals(
            "2026-11-02 12:00,\"Fund \"\"A\"\", Inc\",donation,12.50,ILS,12.50,ILS,10.00,-12.50,false",
            csv[2]
        )
    }

    @Test
    fun `csv lists the stored amount next to the converted one`() {
        val stored = income("Salary", at(2026, 10, 1), 1000F).copy(id = 7, currency = "USD")
        val shown = stored.copy(amount = 3700F, currency = "ILS")
        val csv = entriesToCsv(listOf(shown), zone) { stored }.lines()
        assertEquals(
            "2026-10-01 12:00,\"Salary\",income,3700.00,ILS,1000.00,USD,10.00,370.00,false",
            csv[1]
        )
    }

    @Test
    fun `csv marks tax-only donations with no maser effect`() {
        val entry = donation("Hospital", at(2026, 11, 2), 100F).copy(isTaxOnly = true)
        val csv = entriesToCsv(listOf(entry), zone).lines()
        assertEquals(
            "2026-11-02 12:00,\"Hospital\",donation,100.00,ILS,100.00,ILS,10.00,0.00,true",
            csv[1]
        )
    }
}
