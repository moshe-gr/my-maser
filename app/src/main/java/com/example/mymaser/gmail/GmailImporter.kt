package com.example.mymaser.gmail

import com.example.mymaser.ai.AiError
import com.example.mymaser.ai.AiInput
import com.example.mymaser.ai.AiResult
import com.example.mymaser.ai.DocumentImporter
import com.example.mymaser.ai.KnownNames
import com.example.mymaser.ai.MAX_FILE_BYTES
import com.example.mymaser.ai.MIME_PDF
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
    suspend fun knownIds(ids: List<String>): List<String>
    suspend fun insert(item: PendingImport)
}

/**
 * Reads new matching emails into [PendingImport]s. Each email is looked at once: what it holds is
 * kept for review, and emails with nothing to add are remembered so they're skipped next time.
 */
class GmailImporter(
    private val store: PendingImportStore,
    private val importer: DocumentImporter
) {
    /**
     * Returns how many emails now wait for review. Stops early, without failing, when the AI
     * quota is used up or AI was turned off; throws [IOException] when the network failed.
     */
    suspend fun run(
        source: GmailSource,
        query: String,
        basis: MaserBasis,
        fallbackCurrency: String,
        knownNames: KnownNames,
        nowMillis: Long
    ): Int {
        val ids = source.search(query)
        if (ids.isEmpty()) return 0
        val known = store.knownIds(ids).toSet()
        var found = 0
        for (id in ids.filterNot { it in known }.take(MAX_PER_RUN)) {
            val message = source.message(id)
            val inputs = buildList {
                val text = listOf(message.subject, message.text).filter { it.isNotBlank() }.joinToString("\n\n")
                if (text.isNotEmpty()) add(AiInput.Text(text))
                var budget = MAX_FILE_BYTES
                message.pdfs.forEach { ref ->
                    val bytes = source.pdf(id, ref)?.takeIf { it.size <= budget } ?: return@forEach
                    budget -= bytes.size
                    add(AiInput.File(bytes, MIME_PDF))
                }
            }
            if (inputs.isEmpty()) {
                store.insert(PendingImport(id, message.subject, "[]", nowMillis, PendingImport.DONE))
                continue
            }
            when (val result = importer.read(inputs, basis, fallbackCurrency, knownNames)) {
                is AiResult.Success -> {
                    val status = if (result.value.isEmpty()) PendingImport.DONE else PendingImport.PENDING
                    store.insert(PendingImport(id, message.subject, entriesToJson(result.value), nowMillis, status))
                    if (result.value.isNotEmpty()) found++
                }
                is AiResult.Error -> when (result.reason) {
                    AiError.Network -> throw IOException("Gemini unreachable")
                    AiError.Failed ->
                        store.insert(PendingImport(id, message.subject, "[]", nowMillis, PendingImport.DONE))
                    AiError.QuotaExceeded, AiError.Busy, AiError.Disabled, AiError.NotConfigured -> return found
                }
            }
        }
        return found
    }

    private companion object {
        /** Keeps one run well inside the free Gemini quota. */
        const val MAX_PER_RUN = 10
    }
}
