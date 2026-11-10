package com.example.mymaser.currency

import com.example.mymaser.history.History
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversionTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun day(year: Int, month: Int, dayOfMonth: Int) =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private fun at(year: Int, month: Int, dayOfMonth: Int) =
        ZonedDateTime.of(year, month, dayOfMonth, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    /** Friday 2024-01-05 and Monday 2024-01-08; the weekend between has no rate. */
    private val rates = RateTable(
        listOf(
            ExchangeRate(day(2024, 1, 5), "ILS", 4.0),
            ExchangeRate(day(2024, 1, 5), "USD", 1.1),
            ExchangeRate(day(2024, 1, 8), "ILS", 4.2),
            ExchangeRate(day(2024, 1, 8), "USD", 1.05)
        )
    )

    @Test
    fun `same currency needs no rate`() {
        assertEquals(10.0, RateTable.EMPTY.convert(10.0, "ILS", "ILS", 0)!!, 0.0001)
    }

    @Test
    fun `euro is the base`() {
        assertEquals(40.0, rates.convert(10.0, "EUR", "ILS", day(2024, 1, 5))!!, 0.0001)
        assertEquals(2.5, rates.convert(10.0, "ILS", "EUR", day(2024, 1, 5))!!, 0.0001)
    }

    @Test
    fun `cross rates go through the euro`() {
        assertEquals(1.1 / 4.0 * 100, rates.convert(100.0, "ILS", "USD", day(2024, 1, 5))!!, 0.0001)
    }

    @Test
    fun `weekends use the last business day before`() {
        assertEquals(40.0, rates.convert(10.0, "EUR", "ILS", day(2024, 1, 7))!!, 0.0001)
        assertEquals(42.0, rates.convert(10.0, "EUR", "ILS", day(2024, 1, 8))!!, 0.0001)
    }

    @Test
    fun `missing or too early rates give null`() {
        assertNull(rates.convert(10.0, "GBP", "ILS", day(2024, 1, 5)))
        assertNull(rates.convert(10.0, "USD", "ILS", day(2024, 1, 4)))
    }

    @Test
    fun `display keeps unconvertible entries and counts them`() {
        val entries = listOf(
            History(id = 1, timeStamp = at(2024, 1, 5), amount = 100F, currency = "ILS"),
            History(id = 2, timeStamp = at(2024, 1, 5), amount = 100F, currency = "EUR"),
            History(id = 3, timeStamp = at(2024, 1, 5), amount = 100F, currency = "GBP")
        )
        val shown = toDisplay(entries, "ILS", rates, zone)
        assertEquals(1, shown.unconverted)
        assertEquals(400F, shown.entries[1].amount, 0.001F)
        assertEquals("ILS", shown.entries[1].currency)
        assertEquals("GBP", shown.entries[2].currency)
        assertEquals(listOf(1, 2), shown.countable("ILS").map { it.id })
    }

    @Test
    fun `balance converts each entry at its own date and leaves out unconverted ones`() {
        val entries = listOf(
            History(timeStamp = at(2024, 1, 5), amount = 1000F, currency = "EUR"),
            History(timeStamp = at(2024, 1, 8), amount = 100F, currency = "ILS", isDonation = true),
            History(timeStamp = at(2024, 1, 8), amount = 999F, currency = "GBP")
        )
        val shown = toDisplay(entries, "ILS", rates, zone)
        assertEquals(400.0 - 100.0, maserBalance(shown, null, "ILS", rates), 0.001)
    }

    @Test
    fun `balance includes the opening adjustment, converted`() {
        val shown = toDisplay(emptyList(), "ILS", rates, zone)
        val adjustment = BalanceAdjustment(10.0, "EUR", day(2024, 1, 8))
        assertEquals(42.0, maserBalance(shown, adjustment, "ILS", rates), 0.001)
    }

    @Test
    fun `opening adjustment is the gap between the old total and the entries`() {
        val entries = listOf(
            History(amount = 1000F),
            History(amount = 30F, isDonation = true)
        )
        assertNull(openingAdjustment(70F, entries))
        assertEquals(5.0, openingAdjustment(75F, entries)!!, 0.001)
        assertNull(openingAdjustment(null, entries))
    }

    @Test
    fun `parses a date range response`() {
        val json = """
            {"amount":1.0,"base":"EUR","start_date":"2024-01-05","end_date":"2024-01-08",
             "rates":{"2024-01-05":{"ILS":4.0,"USD":1.1},"2024-01-08":{"ILS":4.2}}}
        """.trimIndent()
        val parsed = parseRates(json).sortedWith(compareBy({ it.epochDay }, { it.currency }))
        assertEquals(
            listOf(
                ExchangeRate(day(2024, 1, 5), "ILS", 4.0),
                ExchangeRate(day(2024, 1, 5), "USD", 1.1),
                ExchangeRate(day(2024, 1, 8), "ILS", 4.2)
            ),
            parsed
        )
    }

    @Test
    fun `parses a single day response`() {
        val json = """{"amount":1.0,"base":"EUR","date":"2024-03-15","rates":{"USD":1.09}}"""
        assertEquals(listOf(ExchangeRate(day(2024, 3, 15), "USD", 1.09)), parseRates(json))
    }
}
