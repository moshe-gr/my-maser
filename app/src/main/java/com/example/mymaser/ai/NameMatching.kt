package com.example.mymaser.ai

import com.example.mymaser.history.History
import com.example.mymaser.history.circularDayDistance
import com.example.mymaser.history.detectRecurrence
import com.example.mymaser.history.localDate
import com.example.mymaser.history.nameKey
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * An imported entry as the review shows it. [entry] carries the name to save, the user's own when
 * one matched; [documentName] is how the document wrote it.
 */
data class ReviewEntry(
    val entry: ExtractedEntry,
    val documentName: String,
    /** A stored entry this one most likely already is. */
    val loggedAs: History? = null,
    /** The same entry appears earlier in this review. */
    val repeat: Boolean = false
)

private const val LOGGED_WINDOW_DAYS = 3
private const val PATTERN_DAY_TOLERANCE = 3
private const val PATTERN_RECENT_AMOUNTS = 3
private const val MIN_MATCHED_CHARS = 3

/** Legal-form words that say nothing about who the payer or organisation is. */
private val NOISE_WORDS = setOf("ער", "בעמ", "ltd", "inc", "llc", "עמותת")

/** Key under which a rename the user made in the review is remembered, per kind. */
fun aliasKey(isDonation: Boolean, documentName: String): String =
    (if (isDonation) "d:" else "i:") + documentName.normalizedKey()

/**
 * Prepares extracted entries for review: each gets the user's name for its source when one can be
 * found, is checked against what's already stored, and repeats within [entries] are marked.
 */
fun prepareReview(
    entries: List<ExtractedEntry>,
    history: List<History>,
    aliases: Map<String, String>,
    now: ZonedDateTime
): List<ReviewEntry> {
    val seen = mutableSetOf<List<Any?>>()
    return entries.map { entry ->
        val sameKind = history.filter { it.isDonation == entry.isDonation }
        val name = resolveName(entry, sameKind, aliases, now)
        val lastTaxCredit = entry.isDonation && sameKind
            .filter { nameKey(it.name) == nameKey(name) }
            .maxByOrNull { it.timeStamp }
            ?.taxCredit == true
        val resolved = entry.copy(name = name, taxCredit = entry.taxCredit || lastTaxCredit)
        val identity = listOf(entry.isDonation, nameKey(name), entry.amount, entry.currency, entry.date)
        ReviewEntry(
            entry = resolved,
            documentName = entry.name,
            loggedAs = findLogged(resolved, sameKind, now),
            repeat = !seen.add(identity)
        )
    }
}

/**
 * The user's name for [entry]'s source, tried in order: a rename they made before, the same name,
 * one name containing the other, a monthly entry with this amount around this day, then the
 * model's pick from the user's names. Falls back to the document's name.
 */
internal fun resolveName(
    entry: ExtractedEntry,
    sameKind: List<History>,
    aliases: Map<String, String>,
    now: ZonedDateTime
): String {
    aliases[aliasKey(entry.isDonation, entry.name)]?.let { return it }
    val names = sameKind.map { it.name.trim() }.distinct()
    val key = entry.name.normalizedKey()
    names.firstOrNull { it.normalizedKey() == key }?.let { return it }
    byContainment(entry.name, names)?.let { return it }
    byPattern(entry, sameKind, now)?.let { return it }
    entry.knownName?.trim()?.let { pick -> names.firstOrNull { it.normalizedKey() == pick.normalizedKey() } }
        ?.let { return it }
    return entry.name.trim()
}

/** The known name whose words all appear in [name] or the other way round; null when unclear. */
internal fun byContainment(name: String, names: List<String>): String? {
    val words = name.words()
    if (words.isEmpty()) return null
    val matches = names.mapNotNull { known ->
        val knownWords = known.words()
        if (knownWords.isEmpty()) return@mapNotNull null
        val shared = when {
            words.containsAll(knownWords) -> knownWords
            knownWords.containsAll(words) -> words
            else -> return@mapNotNull null
        }
        if (shared.sumOf { it.length } < MIN_MATCHED_CHARS) null else known to shared.size
    }
    val best = matches.maxOfOrNull { it.second } ?: return null
    return matches.filter { it.second == best }.map { it.first }.singleOrNull()
}

/** The one recurring source that recently had exactly this amount, due around the entry's day. */
private fun byPattern(entry: ExtractedEntry, sameKind: List<History>, now: ZonedDateTime): String? {
    val day = entry.date?.dayOfMonth ?: return null
    return sameKind.groupBy { it.name.trim() }.filter { (_, list) ->
        val recent = list.filter { it.currency == entry.currency }
            .sortedByDescending { it.timeStamp }
            .take(PATTERN_RECENT_AMOUNTS)
        val recurrence = detectRecurrence(list, now)
        recurrence != null && recent.any { it.amount == entry.amount } &&
            circularDayDistance(recurrence.typicalDay, day) <= PATTERN_DAY_TOLERANCE
    }.keys.singleOrNull()
}

private fun findLogged(entry: ExtractedEntry, sameKind: List<History>, now: ZonedDateTime): History? {
    val date = entry.date ?: return null
    val key = nameKey(entry.name)
    return sameKind
        .filter { nameKey(it.name) == key && it.currency == entry.currency && it.amount == entry.amount }
        .map { it to abs(ChronoUnit.DAYS.between(it.localDate(now.zone), date)) }
        .filter { it.second <= LOGGED_WINDOW_DAYS }
        .minByOrNull { it.second }
        ?.first
}

internal fun String.normalizedKey() = trim().lowercase().replace(Regex("\\s+"), " ")

private val QUOTES = Regex("[\"'`״׳]")
private val PUNCTUATION = Regex("[^\\p{L}\\p{N}]+")

/** Lower-case words without punctuation or legal-form words; ע"ר and בע"מ become one word. */
private fun String.words(): Set<String> =
    lowercase().replace(QUOTES, "").split(PUNCTUATION).filter { it.isNotEmpty() && it !in NOISE_WORDS }.toSet()
