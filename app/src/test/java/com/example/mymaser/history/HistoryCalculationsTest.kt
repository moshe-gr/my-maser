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
    fun `an entry uses the percentage it was saved with`() {
        val savedAtTwenty = History(amount = 1000F, isDonation = false, maserPercentage = 20F)
        assertEquals(200F, savedAtTwenty.maserDelta, 0.001F)
    }

    @Test
    fun `entries default to ten percent`() {
        assertEquals(100F, History(amount = 1000F).maserDelta, 0.001F)
    }

    private val filterSample = listOf(
        History(id = 1, timeStamp = at(2026, 11, 10), name = "Salary", amount = 1F),
        History(id = 2, timeStamp = at(2026, 11, 2), name = "Local charity", amount = 1F, isDonation = true),
        History(id = 3, timeStamp = at(2025, 6, 1), name = "Charity fund", amount = 1F, isDonation = true),
        History(id = 4, timeStamp = at(2024, 3, 1), name = "Bonus", amount = 1F)
    )

    private fun ids(query: HistoryQuery) =
        filterHistory(filterSample, query, now, DayOfWeek.SUNDAY).map { it.id }

    @Test
    fun `empty query keeps everything and is inactive`() {
        assertEquals(listOf(1, 2, 3, 4), ids(HistoryQuery()))
        assertFalse(HistoryQuery().isActive)
    }

    @Test
    fun `search matches names case-insensitively and ignores surrounding spaces`() {
        assertEquals(listOf(2, 3), ids(HistoryQuery(text = "  CHARITY ")))
    }

    @Test
    fun `type and time filters combine`() {
        assertEquals(listOf(2, 3), ids(HistoryQuery(type = TypeFilter.Donations)))
        assertEquals(listOf(2), ids(HistoryQuery(type = TypeFilter.Donations, time = TimeFilter.ThisYear)))
    }

    @Test
    fun `time filters use calendar boundaries`() {
        assertEquals(listOf(1), ids(HistoryQuery(time = TimeFilter.Today)))
        assertEquals(listOf(1, 2), ids(HistoryQuery(time = TimeFilter.ThisMonth)))
        assertEquals(listOf(3), ids(HistoryQuery(time = TimeFilter.LastYear)))
        assertEquals(listOf(4), ids(HistoryQuery(time = TimeFilter.SpecificYear, year = 2024)))
        assertEquals(
            listOf(3),
            ids(HistoryQuery(time = TimeFilter.SpecificMonth, month = YearMonth.of(2025, 6)))
        )
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
