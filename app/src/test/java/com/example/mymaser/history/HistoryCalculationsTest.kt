package com.example.mymaser.history

import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCalculationsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 11, 10, 15, 30, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun donation(time: Long, amount: Float = 10F) =
        History(timeStamp = time, amount = amount, isDonation = true)

    @Test
    fun `month range covers whole days and excludes next month`() {
        val range = monthRange(now)
        assertTrue(at(2026, 11, 1, 0) in range)
        assertTrue(at(2026, 11, 30, 23) in range)
        assertFalse(at(2026, 10, 31, 23) in range)
        assertFalse(at(2026, 12, 1, 0) in range)
    }

    @Test
    fun `year and previous year ranges meet without overlap`() {
        assertEquals(previousYearRange(now).endExclusive, yearRange(now).start)
        assertTrue(at(2026, 1, 1, 0) in yearRange(now))
        assertFalse(at(2026, 1, 1, 0) in previousYearRange(now))
        assertTrue(at(2025, 12, 31, 23) in previousYearRange(now))
    }

    @Test
    fun `month stats stay on current month when computed repeatedly`() {
        val history = listOf(donation(at(2026, 11, 2), 30F), donation(at(2026, 12, 2), 99F))
        val first = computeHomeStats(history, now)
        val second = computeHomeStats(history, now)
        assertEquals(30.0, first.donatedThisMonth, 0.001)
        assertEquals(first, second)
    }

    @Test
    fun `stats count only donations`() {
        val history = listOf(
            donation(at(2026, 11, 2), 30F),
            History(timeStamp = at(2026, 11, 3), amount = 500F, isDonation = false),
            donation(at(2024, 5, 2), 20F)
        )
        val stats = computeHomeStats(history, now)
        assertEquals(30.0, stats.donatedThisMonth, 0.001)
        assertEquals(30.0, stats.donatedThisYear, 0.001)
        assertEquals(50.0, stats.donatedTotal, 0.001)
        assertEquals(at(2026, 11, 3), stats.lastActionTime)
    }

    @Test
    fun `empty history has no last action`() {
        assertNull(computeHomeStats(emptyList(), now).lastActionTime)
    }

    @Test
    fun `income adds a tenth and donation subtracts full amount`() {
        assertEquals(100F, maserDelta(1000F, isDonation = false), 0.001F)
        assertEquals(-50F, maserDelta(50F, isDonation = true), 0.001F)
    }

    @Test
    fun `delete after add restores the original total`() {
        val total = 200F
        val added = total + maserDelta(1000F, false)
        assertEquals(total, added - maserDelta(1000F, false), 0.001F)
    }

    @Test
    fun `restore after delete returns the deleted amount`() {
        val total = 200F
        val deleted = total - maserDelta(70F, true)
        assertEquals(270F, deleted, 0.001F)
        assertEquals(total, deleted + maserDelta(70F, true), 0.001F)
    }

    @Test
    fun `editing an income applies a tenth of the difference`() {
        val original = History(amount = 1000F, isDonation = false)
        assertEquals(50F, maserDeltaForEdit(original, 1500F), 0.001F)
    }

    @Test
    fun `editing a donation applies the inverse difference`() {
        val original = History(amount = 100F, isDonation = true)
        assertEquals(-20F, maserDeltaForEdit(original, 120F), 0.001F)
        assertEquals(30F, maserDeltaForEdit(original, 70F), 0.001F)
    }

    @Test
    fun `entries group into periods in input order`() {
        val history = listOf(
            donation(at(2026, 11, 10)),
            donation(at(2026, 11, 9)),
            donation(at(2026, 11, 3)),
            donation(at(2026, 10, 15))
        )
        val periods = groupByPeriod(history, now, DayOfWeek.SUNDAY).map { it.first }
        assertEquals(
            listOf(
                HistoryPeriod.Today,
                HistoryPeriod.ThisWeek,
                HistoryPeriod.ThisMonth,
                HistoryPeriod.Month(YearMonth.of(2026, 10))
            ),
            periods
        )
    }

    @Test
    fun `suggested amount prefers the same time of month`() {
        val entries = listOf(
            donation(at(2026, 10, 10), 180F),
            donation(at(2026, 9, 20), 50F),
            donation(at(2026, 8, 20), 50F)
        )
        assertEquals(180F, mostLikelyAmount(entries, now), 0.001F)
    }

    @Test
    fun `suggested amount falls back to the most common`() {
        val entries = listOf(
            donation(at(2026, 9, 20), 50F),
            donation(at(2026, 8, 20), 50F),
            donation(at(2026, 7, 20), 75F)
        )
        assertEquals(50F, mostLikelyAmount(entries, now), 0.001F)
    }

    @Test
    fun `whole amounts are filled without a decimal point`() {
        assertEquals("1000", formatAmountInput(1000F))
        assertEquals("12.5", formatAmountInput(12.5F))
    }

    @Test
    fun `amount input accepts comma and rejects invalid text`() {
        assertEquals("12.5", sanitizeAmountInput("12,5"))
        assertEquals("", sanitizeAmountInput(""))
        assertNull(sanitizeAmountInput("12.5.1"))
        assertNull(sanitizeAmountInput("abc"))
    }
}
