package com.example.mymaser.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun now(month: Int, day: Int) = ZonedDateTime.of(2026, month, day, 15, 0, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    /** Salary on the 10th of Jul–Oct 2026. */
    private val salary = (7..10).map {
        History(timeStamp = at(2026, it, 10), name = "Salary", amount = 12000F)
    }

    @Test
    fun `due on the expected day`() {
        val due = predictDueEntries(salary, emptySet(), now(11, 10)).single()
        assertEquals(DueStatus.DueNow, due.status)
        assertEquals(LocalDate.of(2026, 11, 10), due.expectedDate)
        assertEquals(12000F, due.amount)
        assertEquals("ILS", due.currency)
        assertEquals(false, due.isDonation)
    }

    @Test
    fun `upcoming three days before and overdue a few days after`() {
        assertEquals(DueStatus.Upcoming, predictDueEntries(salary, emptySet(), now(11, 7)).single().status)
        assertEquals(DueStatus.DueNow, predictDueEntries(salary, emptySet(), now(11, 12)).single().status)
        assertEquals(DueStatus.Overdue, predictDueEntries(salary, emptySet(), now(11, 15)).single().status)
    }

    @Test
    fun `nothing far from the expected day`() {
        assertTrue(predictDueEntries(salary, emptySet(), now(11, 1)).isEmpty())
        assertTrue(predictDueEntries(salary, emptySet(), now(11, 25)).isEmpty())
    }

    @Test
    fun `already logged this month means nothing is due`() {
        val logged = salary + History(timeStamp = at(2026, 11, 9), name = "salary", amount = 12000F)
        assertTrue(predictDueEntries(logged, emptySet(), now(11, 12)).isEmpty())
    }

    @Test
    fun `dismissed for this month is skipped`() {
        val key = predictionKey("Salary", false, YearMonth.of(2026, 11))
        assertTrue(predictDueEntries(salary, setOf(key), now(11, 10)).isEmpty())
    }

    @Test
    fun `month-end sources are found across the month boundary`() {
        val rent = listOf(
            History(timeStamp = at(2026, 8, 31), name = "Rent", amount = 1F, isDonation = true),
            History(timeStamp = at(2026, 9, 30), name = "Rent", amount = 1F, isDonation = true),
            History(timeStamp = at(2026, 10, 31), name = "Rent", amount = 1F, isDonation = true)
        )
        val due = predictDueEntries(rent, emptySet(), now(11, 29)).single()
        assertEquals(LocalDate.of(2026, 11, 30), due.expectedDate)
        assertEquals(true, due.isDonation)
    }

    @Test
    fun `non-recurring sources are never predicted`() {
        val oneOff = listOf(History(timeStamp = at(2026, 10, 10), name = "Bonus", amount = 5F))
        assertTrue(predictDueEntries(oneOff, emptySet(), now(11, 10)).isEmpty())
    }

    @Test
    fun `pruning keeps only nearby months`() {
        val keys = setOf(
            predictionKey("A", false, YearMonth.of(2026, 10)),
            predictionKey("A", false, YearMonth.of(2026, 11)),
            predictionKey("A", false, YearMonth.of(2026, 5))
        )
        assertEquals(2, pruneDismissed(keys, LocalDate.of(2026, 11, 10)).size)
    }
}
