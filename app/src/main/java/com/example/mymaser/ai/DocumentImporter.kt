package com.example.mymaser.ai

import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate

/** Reads documents into entries for the user to review; nothing is saved here. */
class DocumentImporter(private val client: AiClient) {

    suspend fun read(
        inputs: List<AiInput>,
        basis: MaserBasis,
        fallbackCurrency: String,
        knownNames: List<String>,
        today: LocalDate = LocalDate.now()
    ): AiResult<List<ExtractedEntry>> =
        when (val reply = client.extract(inputs)) {
            is AiResult.Error -> reply
            is AiResult.Success -> AiResult.Success(
                parseExtraction(reply.value, basis, fallbackCurrency, today)
                    .map { it.copy(name = matchKnownName(it.name, knownNames)) }
            )
        }
}
