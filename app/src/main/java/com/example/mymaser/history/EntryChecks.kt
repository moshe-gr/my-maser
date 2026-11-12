package com.example.mymaser.history

import java.time.Duration
import kotlin.math.abs

/** Something about a new entry that looks like a mistake; the user can still save it. */
sealed interface EntryWarning {
    data class Duplicate(val existing: History) : EntryWarning
    data class UnusualAmount(val usual: Float, val currency: String) : EntryWarning
}

private val DUPLICATE_WINDOW: Duration = Duration.ofHours(24)
private const val MIN_HISTORY_FOR_USUAL = 3
private const val USUAL_LOOKBACK = 6
private const val UNUSUAL_FACTOR = 5f

/**
 * Warns about a likely duplicate (same source, amount and currency within a day of [nowMillis],
 * the new entry's time) or a likely typo (at least [UNUSUAL_FACTOR] times above or below the
 * source's usual amount). Returns null when nothing looks off.
 */
fun checkEntry(
    history: List<History>,
    name: String,
    amount: Float,
    currency: String,
    isDonation: Boolean,
    nowMillis: Long
): EntryWarning? {
    val key = nameKey(name)
    val sameSource = history
        .filter { it.isDonation == isDonation && nameKey(it.name) == key && it.currency == currency }
        .sortedByDescending { it.timeStamp }

    sameSource.firstOrNull {
        it.amount == amount && abs(nowMillis - it.timeStamp) <= DUPLICATE_WINDOW.toMillis()
    }?.let { return EntryWarning.Duplicate(it) }

    if (sameSource.size < MIN_HISTORY_FOR_USUAL) return null
    val recent = sameSource.take(USUAL_LOOKBACK).map { it.amount }.sorted()
    val usual = recent[recent.size / 2]
    if (usual <= 0F) return null
    val unusual = amount >= usual * UNUSUAL_FACTOR || amount <= usual / UNUSUAL_FACTOR
    return if (unusual) EntryWarning.UnusualAmount(usual, currency) else null
}
