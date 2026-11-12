package com.example.mymaser.reminders

import com.example.mymaser.history.DueStatus
import com.example.mymaser.history.History
import com.example.mymaser.history.Prediction
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRulesTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun now(year: Int, month: Int, day: Int) = ZonedDateTime.of(year, month, day, 19, 0, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val on = ReminderSettings(enabled = true)

    private fun input(
        now: ZonedDateTime,
        history: List<History> = emptyList(),
        balance: Double = 0.0,
        predictions: List<Prediction> = emptyList(),
        settings: ReminderSettings = on,
        sent: Set<String> = emptySet()
    ) = ReminderInput(history, balance, "ILS", predictions, settings, sent, now)

    private fun prediction(status: DueStatus) = Prediction(
        name = "Salary",
        isDonation = false,
        expectedDate = LocalDate.of(2026, 11, 10),
        amount = 12000F,
        currency = "ILS",
        status = status
    )

    @Test
    fun `nothing when reminders are off`() {
        val result = computeReminders(
            input(now(2026, 11, 3), balance = 500.0, settings = ReminderSettings(enabled = false))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `only overdue predictions become reminders`() {
        val result = computeReminders(
            input(
                now(2026, 11, 15),
                predictions = listOf(prediction(DueStatus.DueNow), prediction(DueStatus.Overdue))
            )
        )
        assertEquals(listOf(Reminder.ExpectedEntry(prediction(DueStatus.Overdue))), result)
    }

    @Test
    fun `monthly balance reminder early in the month when no recent donation`() {
        val old = History(timeStamp = at(2026, 9, 1), amount = 10F, isDonation = true)
        val result = computeReminders(input(now(2026, 11, 3), listOf(old), balance = 500.0))
        assertEquals(listOf("balance|2026-11"), result.map { it.key })
    }

    @Test
    fun `no monthly balance reminder after a recent donation or late in the month`() {
        val recent = History(timeStamp = at(2026, 10, 25), amount = 10F, isDonation = true)
        assertTrue(computeReminders(input(now(2026, 11, 3), listOf(recent), balance = 500.0)).isEmpty())
        assertTrue(computeReminders(input(now(2026, 11, 20), balance = 500.0)).isEmpty())
    }

    @Test
    fun `a recent tax-only donation doesn't hold back the monthly balance reminder`() {
        val taxOnly = History(timeStamp = at(2026, 10, 25), amount = 10F, isDonation = true, isTaxOnly = true)
        val result = computeReminders(input(now(2026, 11, 3), listOf(taxOnly), balance = 500.0))
        assertEquals(listOf("balance|2026-11"), result.map { it.key })
    }

    @Test
    fun `year-end balance reminder in the second half of December`() {
        val result = computeReminders(input(now(2026, 12, 16), balance = 300.0))
        val balance = result.single() as Reminder.Balance
        assertTrue(balance.yearEnd)
        assertEquals("balance-year|2026", balance.key)
    }

    @Test
    fun `no balance reminder when nothing is owed`() {
        assertTrue(computeReminders(input(now(2026, 12, 16), balance = 0.0)).isEmpty())
    }

    @Test
    fun `year in review in early January`() {
        val history = listOf(
            History(timeStamp = at(2026, 6, 1), amount = 300F, isDonation = true),
            History(timeStamp = at(2025, 6, 1), amount = 200F, isDonation = true),
            History(timeStamp = at(2026, 6, 1), amount = 9000F)
        )
        val result = computeReminders(input(now(2027, 1, 3), history))
        assertEquals(listOf(Reminder.YearInReview(2026, 300.0, 200.0, "ILS")), result)
    }

    @Test
    fun `already sent reminders are not repeated`() {
        val result = computeReminders(input(now(2026, 12, 16), balance = 300.0, sent = setOf("balance-year|2026")))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `disabled types are skipped`() {
        val settings = on.copy(balance = false)
        assertTrue(computeReminders(input(now(2026, 12, 16), balance = 300.0, settings = settings)).isEmpty())
    }

    @Test
    fun `old sent keys are pruned`() {
        val keys = setOf("balance|2024-05", "balance|2025-11", "review|2026", "expected|i|salary|2026-11")
        assertEquals(
            setOf("balance|2025-11", "review|2026", "expected|i|salary|2026-11"),
            pruneSentKeys(keys, LocalDate.of(2026, 11, 10))
        )
    }
}
