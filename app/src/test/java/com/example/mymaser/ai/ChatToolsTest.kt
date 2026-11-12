package com.example.mymaser.ai

import com.example.mymaser.history.History
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatToolsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 11, 12, 10, 0, 0, 0, zone)

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val entries = listOf(
        History(timeStamp = at(2026, 1, 10), name = "Acme", amount = 10_000F, maserPercentage = 10F),
        History(timeStamp = at(2026, 2, 10), name = "Acme", amount = 10_000F, maserPercentage = 10F),
        History(timeStamp = at(2026, 2, 15), name = "Yad Eliezer", amount = 500F, isDonation = true, maserPercentage = 10F),
        History(timeStamp = at(2026, 3, 1), name = "Kupat Ha'ir", amount = 300F, isDonation = true, maserPercentage = 10F),
        History(timeStamp = at(2025, 12, 31), name = "Yad Eliezer", amount = 999F, isDonation = true, maserPercentage = 10F)
    )
    private val data = ChatData(entries, "ILS", balance = 1200.0, forecast = null, now = now)

    @Test
    fun `totals for a range with top recipients`() {
        val result = ChatTools.run(ChatTools.TOTALS, mapOf("from" to "2026-01-01", "to" to null), data)
        assertEquals(20_000.0, result["income_total"])
        assertEquals(2_000.0, result["maser_from_income"])
        assertEquals(800.0, result["donation_total"])
        @Suppress("UNCHECKED_CAST")
        val top = result["top_donation_recipients"] as List<Map<String, Any>>
        assertEquals("Yad Eliezer", top.first()["name"])
    }

    @Test
    fun `totals filtered by name ignore case`() {
        val result = ChatTools.run(ChatTools.TOTALS, mapOf("name" to "yad"), data)
        assertEquals(1499.0, result["donation_total"])
        assertEquals(0.0, result["income_total"])
    }

    @Test
    fun `monthly totals for a year`() {
        val result = ChatTools.run(ChatTools.MONTHLY, mapOf("year" to "2026"), data)
        @Suppress("UNCHECKED_CAST")
        val months = result["months"] as List<Map<String, Any>>
        assertEquals(12, months.size)
        assertEquals(500.0, months[1]["donations"])
    }

    @Test
    fun `balance and bad arguments`() {
        assertEquals(1200.0, ChatTools.run(ChatTools.BALANCE, emptyMap(), data)["maser_left_to_give"])
        assertNull(ChatTools.run(ChatTools.BALANCE, emptyMap(), data)["projected_on_december_31"])
        assertNotNull(ChatTools.run(ChatTools.TOTALS, mapOf("from" to "last week"), data)["error"])
        assertNotNull(ChatTools.run("drop_tables", emptyMap(), data)["error"])
    }
}
