package com.example.mymaser.gmail

import com.example.mymaser.ai.AiError
import com.example.mymaser.ai.AiInput
import com.example.mymaser.ai.AiResult
import com.example.mymaser.ai.DocumentImporter
import com.example.mymaser.ai.KnownNames
import com.example.mymaser.ai.MAX_FILE_BYTES
import com.example.mymaser.ai.MIME_PDF
import com.example.mymaser.ai.Unlocked
import com.example.mymaser.ai.entriesToJson
import com.example.mymaser.settings.MaserBasis
import java.io.IOException

/** Where mail comes from; [GmailApi] in the app, a fake in tests. */
interface GmailSource {
    suspend fun search(query: String): List<String>
    suspend fun message(id: String): GmailMessage
    suspend fun pdf(messageId: String, ref: GmailMessage.PdfRef): ByteArray?
}

/** What the importer needs to store; [PendingImportDao] in the app. */
interface PendingImportStore {
    /** Ids already settled, so not read again; emails waiting for a retry aren't among them. */
    suspend fun knownIds(ids: List<String>): List<String>
    suspend fun retryIds(ids: List<String>): List<String>

    /** Adds the email, or replaces its retry record. */
    suspend fun insert(item: PendingImport)
}

/** [found] emails now wait for review; [stoppedBy] is why the run ended before reading them all. */
data class GmailRun(val found: Int, val stoppedBy: AiError? = null)

/**
 * Reads new matching emails into [PendingImport]s. Each email is looked at once: what it holds is
 * kept for review, and emails with nothing to add are remembered so they're skipped next time.
 * [log] gets one line per step, with message ids but never mail content.
 */
class GmailImporter(
    private val store: PendingImportStore,
    private val importer: DocumentImporter,
    private val unlock: suspend (List<AiInput>) -> Unlocked = { Unlocked(it, locked = 0, unreadable = 0) },
    private val log: (String) -> Unit = {}
) {
    /**
     * Stops early, without failing, when the AI quota is used up or AI was turned off; throws
     * [IOException] when the network failed.
     * An email with a PDF the saved password can't open is left for a later run, so it's read once
     * the right password is saved.
     */
    suspend fun run(
        source: GmailSource,
        query: String,
        basis: MaserBasis,
        fallbackCurrency: String,
        knownNames: KnownNames,
        nowMillis: Long
    ): GmailRun {
        val ids = source.search(query)
        if (ids.isEmpty()) {
            log("Search matched no emails")
            return GmailRun(0)
        }
        val known = store.knownIds(ids).toSet()
        val unread = ids.filterNot { it in known }
        val batch = unread.take(MAX_PER_RUN)
        val retrying = if (batch.isEmpty()) emptySet() else store.retryIds(batch).toSet()
        log("Search matched ${ids.size}, already read ${known.size}, reading ${batch.size} of ${unread.size}")
        var found = 0
        for (id in batch) {
            val message = source.message(id)
            val attached = buildList {
                val text = listOf(message.subject, message.text).filter { it.isNotBlank() }.joinToString("\n\n")
                if (text.isNotEmpty()) add(AiInput.Text(text))
                var budget = MAX_FILE_BYTES
                message.pdfs.forEach { ref ->
                    val bytes = source.pdf(id, ref)?.takeIf { it.size <= budget } ?: return@forEach
                    budget -= bytes.size
                    add(AiInput.File(bytes, MIME_PDF))
                }
            }
            val opened = unlock(attached)
            val parts = "text=${message.text.length} pdfs=${message.pdfs.size} attached=${attached.size}"
            if (opened.locked > 0) {
                log("$id: $parts, ${opened.locked} PDF(s) locked with no matching password, left for later")
                continue
            }
            val inputs = opened.inputs
            if (inputs.isEmpty()) {
                log("$id: $parts, nothing readable (${opened.unreadable} unreadable PDF(s)), done")
                store.insert(PendingImport(id, message.subject, "[]", nowMillis, PendingImport.DONE))
                continue
            }
            when (val result = importer.read(inputs, basis, fallbackCurrency, knownNames)) {
                is AiResult.Success -> {
                    val status = if (result.value.isEmpty()) PendingImport.DONE else PendingImport.PENDING
                    log("$id: $parts, ${result.value.size} entr(ies), $status")
                    store.insert(PendingImport(id, message.subject, entriesToJson(result.value), nowMillis, status))
                    if (result.value.isNotEmpty()) found++
                }
                is AiResult.Error -> when (result.reason) {
                    AiError.Network -> throw IOException("Gemini unreachable")
                    AiError.Failed -> {
                        val status = if (id in retrying) PendingImport.DONE else PendingImport.RETRY
                        log("$id: $parts, AI couldn't read it, ${if (status == PendingImport.DONE) "gave up" else "will retry"}")
                        store.insert(PendingImport(id, message.subject, "[]", nowMillis, status))
                    }
                    AiError.QuotaExceeded, AiError.Busy, AiError.Disabled, AiError.NotConfigured -> {
                        log("$id: stopped, AI ${result.reason}")
                        return GmailRun(found, stoppedBy = result.reason)
                    }
                }
            }
        }
        return GmailRun(found)
    }

    private companion object {
        /** Keeps one run well inside the free Gemini quota. */
        const val MAX_PER_RUN = 10
    }
}
