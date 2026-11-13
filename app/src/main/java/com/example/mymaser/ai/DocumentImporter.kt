package com.example.mymaser.ai

import com.example.mymaser.history.History
import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate

/** The user's existing names, offered to the model so it can say which one a document is from. */
data class KnownNames(val incomes: List<String>, val donations: List<String>)

/** Each kind's names, most recently used first. */
fun knownNamesOf(history: List<History>): KnownNames {
    val recentFirst = history.sortedByDescending { it.timeStamp }
    fun names(isDonation: Boolean) =
        recentFirst.filter { it.isDonation == isDonation }.map { it.name.trim() }.distinct()
    return KnownNames(incomes = names(false), donations = names(true))
}

private const val MAX_KNOWN_NAMES = 60

/** Reads documents into entries for the user to review; nothing is saved here. */
class DocumentImporter(
    private val client: AiClient,
    private val isEnabled: suspend () -> Boolean
) {

    suspend fun read(
        inputs: List<AiInput>,
        basis: MaserBasis,
        fallbackCurrency: String,
        knownNames: KnownNames,
        today: LocalDate = LocalDate.now()
    ): AiResult<List<ExtractedEntry>> {
        if (!isEnabled()) return AiResult.Error(AiError.Disabled)
        val request = listOfNotNull(knownNamesHint(knownNames)?.let { AiInput.Text(it) }) + inputs
        return when (val reply = client.extract(request)) {
            is AiResult.Error -> reply
            is AiResult.Success -> parseExtraction(reply.value, basis, fallbackCurrency, today)
                ?.let { AiResult.Success(it) }
                ?: AiResult.Error(AiError.Failed)
        }
    }
}

/** Names go in before the document, labelled so the model doesn't read them as its content. */
internal fun knownNamesHint(names: KnownNames): String? {
    val incomes = names.incomes.take(MAX_KNOWN_NAMES)
    val donations = names.donations.take(MAX_KNOWN_NAMES)
    if (incomes.isEmpty() && donations.isEmpty()) return null
    return buildString {
        appendLine("The user's existing names, for knownName only (not part of the document):")
        if (incomes.isNotEmpty()) appendLine("Income: ${incomes.joinToString(" | ")}")
        if (donations.isNotEmpty()) appendLine("Donations: ${donations.joinToString(" | ")}")
    }.trim()
}
