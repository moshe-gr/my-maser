package com.example.mymaser.history

import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryChecksAndForecastTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 11, 10, 15, 0, 0, 0, zone)
    private val nowMillis = now.toInstant().toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val charity = listOf(
        History(timeStamp = at(2026, 8, 1), name = "Charity", amount = 100F, isDonation = true),
        History(timeStamp = at(2026, 9, 1), name = "Charity", amount = 120F, isDonation = true),
        History(timeStamp = at(2026, 10, 1), name = "Charity", amount = 100F, isDonation = true)
    )

    @Test
    fun `same amount for the same source within a day is a likely duplicate`() {
        val earlier = History(timeStamp = nowMillis - 3_600_000, name = "Charity ", amount = 180F, isDonation = true)
        val warning = checkEntry(listOf(earlier), "charity", 180F, "ILS", true, nowMillis)
        assertEquals(EntryWarning.Duplicate(earlier), warning)
    }

    @Test
    fun `an imported entry dated before an existing one is still a duplicate`() {
        val logged = History(timeStamp = nowMillis, name = "Acme", amount = 9000F)
        val docTime = nowMillis - 3 * 3_600_000
        assertEquals(EntryWarning.Duplicate(logged), checkEntry(listOf(logged), "Acme", 9000F, "ILS", false, docTime))
        assertNull(checkEntry(listOf(logged), "Acme", 9000F, "ILS", false, nowMillis - 48 * 3_600_000))
    }

    @Test
    fun `different amount, currency or type is not a duplicate`() {
        val earlier = History(timeStamp = nowMillis - 3_600_000, name = "Charity", amount = 180F, isDonation = true)
        assertNull(checkEntry(listOf(earlier), "Charity", 181F, "ILS", true, nowMillis))
        assertNull(checkEntry(listOf(earlier), "Charity", 180F, "USD", true, nowMillis))
        assertNull(checkEntry(listOf(earlier), "Charity", 180F, "ILS", false, nowMillis))
    }

    @Test
    fun `an extra zero is flagged against the usual amount`() {
        val warning = checkEntry(charity, "Charity", 1000F, "ILS", true, nowMillis)
        assertEquals(EntryWarning.UnusualAmount(100F, "ILS"), warning)
        assertTrue(checkEntry(charity, "Charity", 10F, "ILS", true, nowMillis) is EntryWarning.UnusualAmount)
    }

    @Test
    fun `normal amounts and short histories are not flagged`() {
        assertNull(checkEntry(charity, "Charity", 300F, "ILS", true, nowMillis))
        assertNull(checkEntry(charity.take(2), "Charity", 5000F, "ILS", true, nowMillis))
    }

    private val salary = (6..10).map {
        History(timeStamp = at(2026, it, 20), name = "Salary", amount = 10000F)
    }

    @Test
    fun `forecast adds the maser share of the remaining salaries`() {
        val forecast = forecastYearEnd(salary, currentBalance = 500.0, now = now)!!
        assertEquals(500.0 + 2 * 1000.0, forecast.balance, 0.001)
        assertEquals(2500.0 / 2, forecast.monthlyToClear, 0.001)
    }

    @Test
    fun `recurring donations reduce the forecast`() {
        val pledge = (6..10).map {
            History(timeStamp = at(2026, it, 25), name = "Pledge", amount = 300F, isDonation = true)
        }
        val forecast = forecastYearEnd(salary + pledge, currentBalance = 0.0, now = now)!!
        assertEquals(2 * 1000.0 - 2 * 300.0, forecast.balance, 0.001)
    }

    @Test
    fun `a salary paid early this month is not counted again`() {
        val paidEarly = salary + History(timeStamp = at(2026, 11, 17), name = "Salary", amount = 10000F)
        val nov18 = ZonedDateTime.of(2026, 11, 18, 15, 0, 0, 0, zone)
        val forecast = forecastYearEnd(paidEarly, 0.0, nov18)!!
        assertEquals(1000.0, forecast.balance, 0.001)
    }

    @Test
    fun `a late salary not logged yet still counts this month`() {
        val onThe5th = (6..10).map { History(timeStamp = at(2026, it, 5), name = "Salary", amount = 10000F) }
        val forecast = forecastYearEnd(onThe5th, 0.0, now)!!
        assertEquals(2 * 1000.0, forecast.balance, 0.001)
    }

    @Test
    fun `a salary logged late this month is not counted again`() {
        val onThe1st = (6..10).map { History(timeStamp = at(2026, it, 1), name = "Salary", amount = 10000F) }
        val loggedLate = onThe1st + History(timeStamp = at(2026, 11, 8), name = "Salary", amount = 10000F)
        val forecast = forecastYearEnd(loggedLate, 0.0, now)!!
        assertEquals(1000.0, forecast.balance, 0.001)
    }

    @Test
    fun `recurring tax-only donations don't reduce the forecast`() {
        val taxOnly = (6..10).map {
            History(timeStamp = at(2026, it, 25), name = "Pledge", amount = 300F, isDonation = true, isTaxOnly = true)
        }
        val forecast = forecastYearEnd(salary + taxOnly, 0.0, now)!!
        assertEquals(2 * 1000.0, forecast.balance, 0.001)
    }

    @Test
    fun `a skipped occurrence is left out`() {
        val skipped = setOf(predictionKey("Salary", false, YearMonth.of(2026, 11)))
        val forecast = forecastYearEnd(salary, 0.0, now, skipped)!!
        assertEquals(1000.0, forecast.balance, 0.001)
    }

    @Test
    fun `a one-off on today's day of the month doesn't set the amount`() {
        val bonus = History(timeStamp = at(2026, 7, 10), name = "Salary", amount = 30000F)
        val forecast = forecastYearEnd(salary + bonus, 0.0, now)!!
        assertEquals(2 * 1000.0, forecast.balance, 0.001)
    }

    @Test
    fun `nothing to forecast without recurring income`() {
        assertNull(forecastYearEnd(charity, 100.0, now))
    }

    @Test
    fun `no monthly amount needed when the year will end clear`() {
        val forecast = forecastYearEnd(salary, currentBalance = -5000.0, now = now)!!
        assertEquals(0.0, forecast.monthlyToClear, 0.001)
    }
}
