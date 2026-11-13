package com.example.mymaser.ai

import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionTest {

    private val today = LocalDate.of(2026, 11, 12)

    private fun parse(json: String, basis: MaserBasis = MaserBasis.Net) =
        parseExtraction(json, basis, "ILS", today)!!

    private val payslip = """
        {"entries":[{"kind":"income","name":"Acme Ltd","amount":12000,
          "grossAmount":15000,"netAmount":12000,"currency":"ILS","date":"2026-10-31"}]}
    """

    @Test
    fun `payslip uses net or gross per the chosen basis`() {
        assertEquals(12000F, parse(payslip, MaserBasis.Net).single().amount)
        assertEquals(15000F, parse(payslip, MaserBasis.Gross).single().amount)
    }

    @Test
    fun `income without the chosen figure falls back to amount`() {
        val json = """{"entries":[{"kind":"income","name":"Acme","amount":900,"netAmount":900}]}"""
        assertEquals(900F, parse(json, MaserBasis.Gross).single().amount)
    }

    @Test
    fun `donation reads amount, currency and date`() {
        val json = """{"entries":[{"kind":"donation","name":" Yad Eliezer ","amount":180,
            "currency":"usd","date":"2026-11-01"}]}"""
        val entry = parse(json).single()
        assertTrue(entry.isDonation)
        assertEquals("Yad Eliezer", entry.name)
        assertEquals("USD", entry.currency)
        assertEquals(LocalDate.of(2026, 11, 1), entry.date)
    }

    @Test
    fun `tax credit is read for donations only and survives storage`() {
        val json = """{"entries":[{"kind":"donation","name":"X","amount":5,"taxCredit":true},
            {"kind":"income","name":"Y","amount":5,"taxCredit":true},
            {"kind":"donation","name":"Z","amount":5}]}"""
        val entries = parse(json)
        assertEquals(listOf(true, false, false), entries.map { it.taxCredit })
        assertEquals(entries, entriesFromJson(entriesToJson(entries)))
    }

    @Test
    fun `unsupported or missing currency falls back`() {
        val json = """{"entries":[{"kind":"donation","name":"X","amount":5,"currency":"JPY"},
            {"kind":"donation","name":"Y","amount":5}]}"""
        assertEquals(listOf("ILS", "ILS"), parse(json).map { it.currency })
    }

    @Test
    fun `future dates become today and malformed dates are dropped`() {
        val json = """{"entries":[{"kind":"income","name":"X","amount":5,"date":"2026-11-30"},
            {"kind":"donation","name":"Y","amount":5,"date":"31/10/2026"}]}"""
        assertEquals(listOf(today, null), parse(json).map { it.date })
    }

    @Test
    fun `json null fields count as missing`() {
        val json = """{"entries":[{"kind":"donation","name":null,"amount":5},
            {"kind":"donation","name":"X","amount":5,"currency":null,"date":null}]}"""
        val entry = parse(json).single()
        assertEquals("ILS", entry.currency)
        assertNull(entry.date)
    }

    @Test
    fun `entries without name, positive amount or known kind are dropped`() {
        val json = """{"entries":[{"kind":"donation","name":"","amount":5},
            {"kind":"donation","name":"X","amount":0},
            {"kind":"refund","name":"X","amount":5},
            {"kind":"income","name":"OK","amount":5}]}"""
        assertEquals(listOf("OK"), parse(json).map { it.name })
    }

    @Test
    fun `unreadable replies are null, an empty list is not`() {
        assertNull(parseExtraction("not json", MaserBasis.Net, "ILS", today))
        assertNull(parseExtraction("""{"entries":[{"kind":"""", MaserBasis.Net, "ILS", today))
        assertNull(parseExtraction("{}", MaserBasis.Net, "ILS", today))
        assertTrue(parse("""{"entries":[]}""").isEmpty())
    }

    @Test
    fun `fenced json with or without a language tag`() {
        val body = "{\"entries\":[{\"kind\":\"income\",\"name\":\"A\",\"amount\":1}]}"
        assertEquals(1, parse("```json\n$body\n```").size)
        assertEquals(1, parse("```\n$body\n```").size)
    }

    @Test
    fun `local currency spellings`() {
        assertEquals("ILS", normalizeCurrency("₪"))
        assertEquals("ILS", normalizeCurrency("ש\"ח"))
        assertEquals("ILS", normalizeCurrency("nis"))
        assertEquals("GBP", normalizeCurrency("£"))
        assertNull(normalizeCurrency("JPY"))
    }

    @Test
    fun `known names keep their stored spelling`() {
        assertEquals("Yad Eliezer", matchKnownName("yad  eliezer ", listOf("Acme", "Yad Eliezer")))
        assertEquals("New Org", matchKnownName(" New Org", listOf("Acme")))
    }
}
