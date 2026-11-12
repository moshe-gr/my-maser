package com.example.mymaser.ai

import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate

/** Reads documents into entries for the user to review; nothing is saved here. */
class DocumentImporter(
    private val client: AiClient,
    private val isEnabled: suspend () -> Boolean
) {

    suspend fun read(
        inputs: List<AiInput>,
        basis: MaserBasis,
        fallbackCurrency: String,
        knownNames: List<String>,
        today: LocalDate = LocalDate.now()
    ): AiResult<List<ExtractedEntry>> {
        if (!isEnabled()) return AiResult.Error(AiError.Disabled)
        return when (val reply = client.extract(inputs)) {
            is AiResult.Error -> reply
            is AiResult.Success -> {
                val entries = parseExtraction(reply.value, basis, fallbackCurrency, today)
                    ?: return AiResult.Error(AiError.Failed)
                AiResult.Success(entries.map { it.copy(name = matchKnownName(it.name, knownNames)) })
            }
        }
    }
}
