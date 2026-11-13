package com.example.mymaser.ai

import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
    val date: LocalDate?,
    /** The model's pick among the user's existing names, when it's clearly the same source. */
    val knownName: String? = null,
    /** A donation the document says counts for the section 46 tax credit. */
    val taxCredit: Boolean = false
)

/** Stores found entries until the user reviews them, e.g. from an email read in the background. */
fun entriesToJson(entries: List<ExtractedEntry>): String = JSONArray(
    entries.map {
        JSONObject()
            .put("isDonation", it.isDonation)
            .put("name", it.name)
            .put("amount", it.amount.toDouble())
            .put("currency", it.currency)
            .put("date", it.date?.toString() ?: JSONObject.NULL)
            .put("knownName", it.knownName ?: JSONObject.NULL)
            .put("taxCredit", it.taxCredit)
    }
).toString()

fun entriesFromJson(json: String): List<ExtractedEntry> = try {
    JSONArray(json).objects().map {
        ExtractedEntry(
            isDonation = it.getBoolean("isDonation"),
            name = it.getString("name"),
            amount = it.getDouble("amount").toFloat(),
            currency = it.getString("currency"),
            date = it.text("date")?.let(::parseDate),
            knownName = it.text("knownName"),
            taxCredit = it.optBoolean("taxCredit")
        )
    }
} catch (_: JSONException) {
    emptyList()
}

/** Noon on the document's date, so it lands on that day in any nearby zone; now when undated. */
fun documentTimeStamp(date: LocalDate?, zone: ZoneId, nowMillis: Long): Long =
    date?.atTime(LocalTime.NOON)?.atZone(zone)?.toInstant()?.toEpochMilli() ?: nowMillis

/** Instructions sent with every document; the reply must use [ExtractionFields]. */
const val EXTRACTION_PROMPT = """
You read financial documents for a ma'aser (tithe) tracker. Documents are often in Hebrew.
Find every income and every donation in the document and return them as JSON.
- A donation is money given to a charity, amuta (ע"ר), synagogue, yeshiva, kollel or a person in
  need. A purchase, invoice (חשבונית), order, bill or subscription from a shop or company is NOT a
  donation and NOT income: return nothing for it.
- amount is the money paid (סכום, סה"כ, total). Never use a receipt, invoice, order, reference,
  ID, phone or account number as the amount. If the paid sum isn't clear, leave the entry out.
- name: copy it exactly as the document writes it, in its own language and script. Never
  translate, transliterate or invent a name; if it can't be read, leave the entry out.
- knownName: the user's existing names may be listed before the document. Only when the entry
  is clearly from the same employer, payer or organisation as one of them, set knownName to
  that name exactly as listed. Otherwise omit it. Still set name as the document writes it.
- Text taken out of a PDF may show Hebrew words or lines in reverse order; read it accordingly.
- Payslip (תלוש שכר): one income. name = employer. grossAmount = ברוטו, netAmount = נטו לתשלום,
  amount = netAmount if known, else grossAmount. date = the pay month's last day.
- Donation receipt (קבלה, תרומה, section 46 / סעיף 46): one donation. name = the organisation.
  taxCredit = true only when the receipt says the donation is recognised under section 46
  (סעיף 46, מוכר לענין זיכוי מס); otherwise omit it.
- Bank or transfer confirmation: income if money came in, donation if it went to a charity.
- A short note the user typed or said, e.g. "gave 180 to Yad Eliezer" or "קיבלתי בונוס 2000":
  one entry per amount mentioned. Omit the date unless the note gives one.
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
    const val KNOWN_NAME = "knownName"
    const val TAX_CREDIT = "taxCredit"
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

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

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
        date = text(f.DATE)?.let(::parseDate)?.let { minOf(it, today) },
        knownName = text(f.KNOWN_NAME),
        taxCredit = isDonation && optBoolean(f.TAX_CREDIT)
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
