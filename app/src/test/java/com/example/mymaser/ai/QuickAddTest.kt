package com.example.mymaser.ai

import com.example.mymaser.history.Prefill
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAddTest {

    private val incomes = listOf("Acme Ltd", "משכורת")
    private val donations = listOf("Yad Eliezer", "יד אליעזר", "Kupat Ha'ir")

    private fun parse(text: String) = parseQuickAdd(text, incomes, donations)

    private fun parsed(name: String, amount: Float, currency: String?, isDonation: Boolean) =
        QuickAdd.Parsed(Prefill(name, amount, currency, isDonation))

    @Test
    fun `english donation with keyword`() {
        assertEquals(parsed("Yad Eliezer", 180F, null, true), parse("gave 180 to yad eliezer"))
    }

    @Test
    fun `hebrew donation with attached prefix matches the known name`() {
        assertEquals(parsed("יד אליעזר", 180F, "ILS", true), parse("תרמתי 180 ש\"ח ליד אליעזר"))
    }

    @Test
    fun `known name alone decides the kind`() {
        assertEquals(parsed("Acme Ltd", 14200F, null, false), parse("acme ltd 14,200"))
    }

    @Test
    fun `income noun becomes the name`() {
        assertEquals(parsed("salary", 9500.5F, "USD", false), parse("salary \$9500.50"))
    }

    @Test
    fun `new hebrew recipient drops the to-prefix`() {
        assertEquals(parsed("ישיבה", 100F, null, true), parse("נתתי 100 לישיבה"))
    }

    @Test
    fun `unclear notes go to the model`() {
        assertEquals(QuickAdd.NeedsAi, parse("180 to someone new"))
        assertEquals(QuickAdd.NeedsAi, parse("gave some money to yad eliezer"))
        assertEquals(QuickAdd.NeedsAi, parse("gave 0 to yad eliezer"))
        assertEquals(QuickAdd.NeedsAi, parse("gave 50"))
    }
}
