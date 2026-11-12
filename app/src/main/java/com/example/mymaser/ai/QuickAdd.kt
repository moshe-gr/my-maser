package com.example.mymaser.ai

import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.history.Prefill

/** What a typed or spoken note like "180 to Yad Eliezer" turned into. */
sealed interface QuickAdd {
    data class Parsed(val prefill: Prefill) : QuickAdd

    /** Too free-form to read locally; worth asking the model. */
    data object NeedsAi : QuickAdd
}

private val NUMBER = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d+))?""")
private const val CURRENCY_SYMBOLS = "₪$€£"

private val DONATION_WORDS = setOf(
    "gave", "give", "donated", "donate", "donation", "tzedakah", "תרמתי", "תרומה", "נתתי", "צדקה"
)
private val INCOME_WORDS = setOf("got", "received", "earned", "income", "קיבלתי", "הכנסה", "הרווחתי")

/** Income words that can also be the entry's name when nothing else is given. */
private val INCOME_NOUNS = setOf("salary", "paycheck", "wage", "wages", "bonus", "משכורת", "שכר", "בונוס")
private val FILLER_WORDS = setOf("to", "for", "from", "the", "a", "an", "of", "on", "ל", "עבור", "של", "מ", "ב", "-")

private val CURRENCY_WORDS = mapOf(
    "ils" to "ILS", "nis" to "ILS", "shekel" to "ILS", "shekels" to "ILS", "ש\"ח" to "ILS",
    "ש״ח" to "ILS", "שח" to "ILS", "שקל" to "ILS", "שקלים" to "ILS",
    "usd" to "USD", "dollar" to "USD", "dollars" to "USD", "דולר" to "USD", "דולרים" to "USD",
    "eur" to "EUR", "euro" to "EUR", "euros" to "EUR", "יורו" to "EUR",
    "gbp" to "GBP", "pound" to "GBP", "pounds" to "GBP", "פאונד" to "GBP"
)

/**
 * Reads a short note offline: an amount, an optional currency, a name, and whether it's income or
 * a donation from keywords or from the names the user already has. Anything unclear is left to
 * the model.
 */
fun parseQuickAdd(text: String, incomeNames: List<String>, donationNames: List<String>): QuickAdd {
    val match = NUMBER.find(text) ?: return QuickAdd.NeedsAi
    val amount = (match.groupValues[1].replace(",", "") + "." + match.groupValues[2].ifEmpty { "0" })
        .toFloatOrNull()?.takeIf { it > 0F } ?: return QuickAdd.NeedsAi

    var currency: String? = null
    val rest = text.removeRange(match.range).map { char ->
        if (char in CURRENCY_SYMBOLS) {
            currency = SupportedCurrency.fromSymbol(char.toString())?.code
            ' '
        } else {
            char
        }
    }.joinToString("")

    var isDonation: Boolean? = null
    var incomeNoun: String? = null
    val nameWords = rest.split(Regex("\\s+")).filter { it.isNotBlank() }.filter { word ->
        val key = word.lowercase().trim(',', '.', '!', '?')
        when {
            key in CURRENCY_WORDS -> { currency = CURRENCY_WORDS.getValue(key); false }
            key in DONATION_WORDS -> { isDonation = true; false }
            key in INCOME_WORDS -> { isDonation = false; false }
            key in INCOME_NOUNS -> { isDonation = false; incomeNoun = word; false }
            key in FILLER_WORDS -> false
            else -> true
        }
    }
    val typed = nameWords.joinToString(" ").trim(',', '.', ' ')

    val (name, kind) = resolveName(typed, isDonation, incomeNames, donationNames)
        ?: return QuickAdd.NeedsAi
    val finalName = name.ifEmpty { incomeNoun.orEmpty() }
    if (finalName.isEmpty()) return QuickAdd.NeedsAi
    return QuickAdd.Parsed(Prefill(finalName, amount, currency, kind))
}

/**
 * The name and kind, matching the user's existing names when possible. A Hebrew "ל" (to) or "מ"
 * (from) stuck to the name is dropped when it fits the kind.
 */
private fun resolveName(
    typed: String,
    isDonation: Boolean?,
    incomeNames: List<String>,
    donationNames: List<String>
): Pair<String, Boolean>? {
    fun known(name: String, donation: Boolean): String? {
        val names = if (donation) donationNames else incomeNames
        return matchKnownName(name, names).takeIf { match -> names.any { it == match } }
    }
    val withoutPrefix = typed.takeIf { it.length > 2 }?.drop(1)
    val candidates = buildList {
        add(typed to null)
        if (typed.startsWith("ל") && withoutPrefix != null) add(withoutPrefix to true)
        if (typed.startsWith("מ") && withoutPrefix != null) add(withoutPrefix to false)
    }
    val kinds = isDonation?.let { listOf(it) } ?: listOf(true, false)
    for ((candidate, prefixKind) in candidates) {
        for (kind in kinds) {
            if (prefixKind != null && prefixKind != kind) continue
            known(candidate, kind)?.let { return it to kind }
        }
    }
    val kind = isDonation ?: return null
    val prefix = if (kind) "ל" else "מ"
    val name = if (withoutPrefix != null && typed.startsWith(prefix)) withoutPrefix else typed
    return name to kind
}
