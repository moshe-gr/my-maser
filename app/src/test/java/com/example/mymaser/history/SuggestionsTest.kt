package com.example.mymaser.history

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 11, 10, 15, 30, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun income(name: String, time: Long, amount: Float = 100F) =
        History(timeStamp = time, name = name, amount = amount)

    private fun donation(name: String, time: Long, amount: Float = 100F) =
        History(timeStamp = time, name = name, amount = amount, isDonation = true)

    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    // Day-of-month distance

    @Test
    fun `same day of month is zero days away`() {
        assertEquals(0, daysFromSameDayOfMonth(date(2026, 9, 10), date(2026, 11, 10)))
    }

    @Test
    fun `distance wraps across the month boundary`() {
        assertEquals(1, daysFromSameDayOfMonth(date(2026, 10, 31), date(2026, 12, 1)))
        assertEquals(1, daysFromSameDayOfMonth(date(2026, 10, 1), date(2026, 11, 30)))
    }

    @Test
    fun `a late day maps to the end of a shorter month`() {
        assertEquals(0, daysFromSameDayOfMonth(date(2026, 1, 31), date(2026, 2, 28)))
        assertEquals(0, daysFromSameDayOfMonth(date(2026, 10, 31), date(2026, 11, 30)))
    }

    @Test
    fun `an early payment counts as near`() {
        assertEquals(1, daysFromSameDayOfMonth(date(2026, 10, 11), date(2026, 11, 10)))
    }

    // Name ranking

    @Test
    fun `names due around today come first, then most recent`() {
        val entries = listOf(
            income("Rent", at(2026, 11, 5)),
            income("Salary", at(2026, 10, 10)),
            income("Bonus", at(2026, 11, 1)),
            income("Freelance", at(2026, 6, 20))
        )
        assertEquals(
            listOf("Salary", "Rent", "Bonus", "Freelance"),
            rankSuggestedNames(entries, now)
        )
    }

    @Test
    fun `a pattern older than the lookback does not count as due`() {
        val entries = listOf(
            income("Old job", at(2024, 6, 10)),
            income("Side gig", at(2026, 9, 3))
        )
        assertEquals(listOf("Side gig", "Old job"), rankSuggestedNames(entries, now))
    }

    @Test
    fun `names merge across case and spaces, keeping the latest spelling`() {
        val entries = listOf(
            income("salary ", at(2026, 8, 3)),
            income("Salary", at(2026, 9, 3)),
            income("  ", at(2026, 9, 4))
        )
        assertEquals(listOf("Salary"), rankSuggestedNames(entries, now))
    }

    @Test
    fun `no entries means no names`() {
        assertEquals(emptyList<String>(), rankSuggestedNames(emptyList(), now))
    }

    // Matching typed text

    @Test
    fun `prefix matches come before other matches, keeping rank order`() {
        val ranked = listOf("Local charity", "Charity fund", "Chabad", "Yeshiva")
        assertEquals(
            listOf("Charity fund", "Chabad", "Local charity"),
            matchSuggestions(ranked, "ch", limit = 5)
        )
    }

    @Test
    fun `the exact name already typed is not suggested`() {
        assertEquals(
            listOf("Charity fund"),
            matchSuggestions(listOf("Charity", "Charity fund"), "charity ", limit = 5)
        )
    }

    @Test
    fun `empty query suggests the top names up to the limit`() {
        assertEquals(listOf("A", "B"), matchSuggestions(listOf("A", "B", "C"), "", limit = 2))
    }

    // Suggested amount

    @Test
    fun `no history gives no amount`() {
        assertNull(suggestedAmount(emptyList(), "Salary", isDonation = false, now = now))
    }

    @Test
    fun `entries of the other type are ignored`() {
        val entries = listOf(donation("Salary", at(2026, 10, 10), 999F))
        assertNull(suggestedAmount(entries, "Salary", isDonation = false, now = now))
    }

    @Test
    fun `name matching ignores case and spaces`() {
        val entries = listOf(income("Salary ", at(2026, 10, 10), 12000F))
        assertEquals(12000F, suggestedAmount(entries, "salary", false, now))
    }

    @Test
    fun `entries around today's date win over other days`() {
        val entries = listOf(
            donation("Charity", at(2026, 11, 3), 50F),
            donation("Charity", at(2026, 11, 1), 50F),
            donation("Charity", at(2026, 10, 9), 180F)
        )
        assertEquals(180F, suggestedAmount(entries, "Charity", true, now))
    }

    @Test
    fun `month-end payment is found on the first of the month`() {
        val firstOfDecember = ZonedDateTime.of(2026, 12, 1, 9, 0, 0, 0, zone)
        val entries = listOf(
            income("Salary", at(2026, 11, 30), 12000F),
            income("Salary", at(2026, 11, 15), 500F),
            income("Salary", at(2026, 11, 16), 500F)
        )
        assertEquals(12000F, suggestedAmount(entries, "Salary", false, firstOfDecember))
    }

    @Test
    fun `a raise wins over a more frequent older amount`() {
        val entries = listOf(
            income("Salary", at(2026, 10, 10), 13000F),
            income("Salary", at(2026, 9, 10), 12000F),
            income("Salary", at(2026, 8, 10), 12000F)
        )
        assertEquals(13000F, suggestedAmount(entries, "Salary", false, now))
    }

    @Test
    fun `a one-off is outvoted by a regular amount`() {
        val entries = listOf(
            income("Salary", at(2026, 10, 10), 20000F),
            income("Salary", at(2026, 9, 10), 12000F),
            income("Salary", at(2026, 8, 10), 12000F),
            income("Salary", at(2026, 7, 10), 12000F)
        )
        assertEquals(12000F, suggestedAmount(entries, "Salary", false, now))
    }

    @Test
    fun `old entries beyond the lookback do not vote`() {
        val recent = (1..6).map { income("Salary", at(2026, 10 - it + 1, 10), 13000F) }
        val old = (1..6).map { income("Salary", at(2025, it, 10), 12000F) }
        assertEquals(13000F, suggestedAmount(recent + old, "Salary", false, now))
    }

    @Test
    fun `input order does not matter`() {
        val entries = listOf(
            income("Salary", at(2026, 8, 10), 12000F),
            income("Salary", at(2026, 10, 10), 13000F),
            income("Salary", at(2026, 9, 10), 12000F)
        )
        assertEquals(13000F, suggestedAmount(entries, "Salary", false, now))
        assertEquals(13000F, suggestedAmount(entries.reversed(), "Salary", false, now))
    }
}
