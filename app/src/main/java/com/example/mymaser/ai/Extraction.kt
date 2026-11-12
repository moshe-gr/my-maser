package com.example.mymaser.ai

import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** An income or donation read from a document, waiting for the user to confirm it. */
data class ExtractedEntry(
    val isDonation: Boolean,
    val name: String,
    val amount: Float,
    val currency: String,
    val date: LocalDate?
)

/** Instructions sent with every document; the reply must use [ExtractionFields]. */
const val EXTRACTION_PROMPT = """
You read financial documents for a ma'aser (tithe) tracker. Documents are often in Hebrew.
Find every income and every donation in the document and return them as JSON.
- Payslip (תלוש שכר): one income. name = employer. grossAmount = ברוטו, netAmount = נטו לתשלום,
  amount = netAmount if known, else grossAmount. date = the pay month's last day.
- Donation receipt (קבלה, תרומה, section 46 / סעיף 46): one donation. name = the organisation.
- Bank or transfer confirmation: income if money came in, donation if it went to a charity.
- currency: ISO code (ILS for ₪/ש"ח/NIS, USD, EUR, GBP). Omit if unknown.
- date: YYYY-MM-DD. Omit if unknown.
- Ignore tax, pension and other deductions as separate entries.
- If the document holds no income or donation, return an empty list.
"""

/** Field names in the model's reply, shared with the response schema. */
object ExtractionFields {
    const val ENTRIES = "entries"
    const val KIND = "kind"
    const val KIND_INCOME = "income"
    const val KIND_DONATION = "donation"
    const val NAME = "name"
    const val AMOUNT = "amount"
    const val GROSS = "grossAmount"
    const val NET = "netAmount"
    const val CURRENCY = "currency"
    const val DATE = "date"
}

/**
 * Reads the model's JSON reply, or returns null when the reply isn't readable at all. Entries
 * without a name or a positive amount are dropped, a currency the app doesn't support falls back
 * to [fallbackCurrency], and dates after [today] become [today]. Payslip income uses gross or net
 * per [basis] when the document shows both.
 */
fun parseExtraction(
    json: String,
    basis: MaserBasis,
    fallbackCurrency: String,
    today: LocalDate
): List<ExtractedEntry>? {
    val entries = try {
        JSONObject(json.withoutCodeFence()).optJSONArray(ExtractionFields.ENTRIES)
    } catch (_: JSONException) {
        null
    } ?: return null
    return entries.objects().mapNotNull { it.toEntry(basis, fallbackCurrency, today) }
}

private fun String.withoutCodeFence(): String {
    val text = trim()
    if (!text.startsWith("```")) return text
    return text.removePrefix("```").removePrefix("json").removeSuffix("```").trim()
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.toEntry(basis: MaserBasis, fallbackCurrency: String, today: LocalDate): ExtractedEntry? {
    val f = ExtractionFields
    val isDonation = when (text(f.KIND)?.lowercase()) {
        f.KIND_DONATION -> true
        f.KIND_INCOME -> false
        else -> return null
    }
    val name = text(f.NAME) ?: return null
    val chosen = if (isDonation) null else when (basis) {
        MaserBasis.Gross -> positive(f.GROSS)
        MaserBasis.Net -> positive(f.NET)
    }
    val amount = chosen ?: positive(f.AMOUNT) ?: positive(f.NET) ?: positive(f.GROSS) ?: return null
    return ExtractedEntry(
        isDonation = isDonation,
        name = name,
        amount = amount.toFloat(),
        currency = normalizeCurrency(text(f.CURRENCY)) ?: fallbackCurrency,
        date = text(f.DATE)?.let(::parseDate)?.let { minOf(it, today) }
    )
}

/** The trimmed value, or null when it's missing, JSON null or blank. */
private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

private fun JSONObject.positive(key: String): Double? =
    optDouble(key).takeIf { !it.isNaN() && it > 0 }

private fun parseDate(text: String): LocalDate? =
    try {
        LocalDate.parse(text.trim())
    } catch (_: DateTimeParseException) {
        null
    }

/** ISO code, symbol or a common local spelling, mapped to a supported currency code. */
fun normalizeCurrency(raw: String?): String? {
    val text = raw?.trim()?.uppercase().orEmpty()
    if (text.isEmpty()) return null
    SupportedCurrency.fromCode(text)?.let { return it.code }
    SupportedCurrency.fromSymbol(text)?.let { return it.code }
    return when (text) {
        "NIS", "ש\"ח", "ש״ח", "שח", "שקל", "שקלים" -> SupportedCurrency.ILS.code
        else -> null
    }
}

/** The existing spelling of [name] when the user already has it, ignoring case and spacing. */
fun matchKnownName(name: String, known: List<String>): String {
    val key = name.normalizedKey()
    return known.firstOrNull { it.normalizedKey() == key } ?: name.trim()
}

private fun String.normalizedKey() = trim().lowercase().replace(Regex("\\s+"), " ")
