package com.example.mymaser.ai

import com.example.mymaser.history.History
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatchingTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val now = ZonedDateTime.of(2026, 11, 13, 12, 0, 0, 0, zone)

    private fun donation(name: String, amount: Float, date: LocalDate) = History(
        timeStamp = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
        name = name,
        amount = amount,
        isDonation = true,
        currency = "ILS"
    )

    /** One entry on the 10th of each of the last six months. */
    private fun monthly(name: String, amount: Float) =
        (1L..6L).map { donation(name, amount, LocalDate.of(2026, 10, 10).minusMonths(it - 1)) }

    private fun extracted(name: String, amount: Float = 100F, date: LocalDate? = null, knownName: String? = null) =
        ExtractedEntry(true, name, amount, "ILS", date, knownName)

    private fun resolve(entry: ExtractedEntry, history: List<History>, aliases: Map<String, String> = emptyMap()) =
        resolveName(entry, history, aliases, now)

    @Test
    fun `a longer official name finds the user's short one`() {
        val names = listOf("שחר טוב", "יוצאי חברון", "תת חברון", "בני תורה", "ועד הרבנים")
        assertEquals("שחר טוב", byContainment("שחר טוב עזרה הדדית (ע\"ר)", names))
        assertEquals("יוצאי חברון", byContainment("יוצאי חברון ועטרת ישראל - קרית יערים", names))
        assertEquals("בני תורה", byContainment("קהילת בני תורה - קרית יערים - אח לאח", names))
        assertEquals("ועד הרבנים", byContainment("ועד הרבנים לעניני צדקה", names))
        assertEquals("Synch", byContainment("Synch Ltd.", listOf("Synch", "דתות")))
    }

    @Test
    fun `containment gives up when two names fit equally or nothing fits`() {
        assertNull(byContainment("קרן חסד ירושלים", listOf("קרן חסד", "חסד ירושלים")))
        assertNull(byContainment("Yad Eliezer", listOf("Ezer Mizion")))
        assertNull(byContainment("א ב", listOf("א")))
    }

    @Test
    fun `a rename the user made before wins`() {
        val aliases = mapOf(aliasKey(true, "Bank charge 4411") to "בירנצוויג")
        assertEquals("בירנצוויג", resolve(extracted(" bank  charge 4411"), emptyList(), aliases))
        assertEquals("Bank charge 4411", resolve(extracted("Bank charge 4411").copy(isDonation = false), emptyList(), aliases))
    }

    @Test
    fun `a monthly amount on its usual day finds an unrelated name`() {
        val history = monthly("בירנצוויג", 500F) + monthly("כולל עמוד היומי", 300F)
        assertEquals("בירנצוויג", resolve(extracted("הוראת קבע 8812", 500F, LocalDate.of(2026, 11, 10)), history))
        assertEquals("הוראת קבע 8812", resolve(extracted("הוראת קבע 8812", 500F, LocalDate.of(2026, 11, 22)), history))
        assertEquals("הוראת קבע 8812", resolve(extracted("הוראת קבע 8812", 450F, LocalDate.of(2026, 11, 10)), history))
    }

    @Test
    fun `the model's pick is used only when it names an existing source`() {
        val history = listOf(donation("קופת העיר", 100F, LocalDate.of(2026, 4, 10)))
        assertEquals("קופת העיר", resolve(extracted("Kupat Hair", knownName = "קופת העיר"), history))
        assertEquals("Kupat Hair", resolve(extracted("Kupat Hair", knownName = "Made up"), history))
    }

    @Test
    fun `an entry already logged under the user's name is found and unticked`() {
        val logged = donation("שחר טוב", 1000F, LocalDate.of(2026, 11, 10))
        val review = prepareReview(
            listOf(extracted("שחר טוב עזרה הדדית (ע\"ר)", 1000F, LocalDate.of(2026, 11, 10))),
            listOf(logged),
            emptyMap(),
            now
        ).single()
        assertEquals("שחר טוב", review.entry.name)
        assertEquals("שחר טוב עזרה הדדית (ע\"ר)", review.documentName)
        assertEquals(logged, review.loggedAs)
    }

    @Test
    fun `other amounts, far dates and undated entries aren't treated as logged`() {
        val history = listOf(donation("שחר טוב", 1000F, LocalDate.of(2026, 11, 10)))
        fun loggedAs(amount: Float, date: LocalDate?) =
            prepareReview(listOf(extracted("שחר טוב", amount, date)), history, emptyMap(), now).single().loggedAs
        assertNull(loggedAs(100F, LocalDate.of(2026, 11, 10)))
        assertNull(loggedAs(1000F, LocalDate.of(2026, 10, 10)))
        assertNull(loggedAs(1000F, null))
    }

    @Test
    fun `a known name's tax credit carries over to the review`() {
        val history = listOf(donation("שחר טוב", 100F, LocalDate.of(2026, 9, 10)).copy(taxCredit = true))
        val review = prepareReview(
            listOf(extracted("שחר טוב עזרה הדדית"), extracted("Other")),
            history,
            emptyMap(),
            now
        )
        assertTrue(review[0].entry.taxCredit)
        assertFalse(review[1].entry.taxCredit)
    }

    @Test
    fun `the same entry twice in one review is marked as a repeat`() {
        val slip = extracted("Acme", 9000F, LocalDate.of(2026, 10, 31))
        val review = prepareReview(listOf(slip, slip, slip.copy(amount = 1F)), emptyList(), emptyMap(), now)
        assertFalse(review[0].repeat)
        assertTrue(review[1].repeat)
        assertFalse(review[2].repeat)
    }
}
