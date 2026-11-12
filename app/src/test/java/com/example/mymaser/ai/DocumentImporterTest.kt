package com.example.mymaser.ai

import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentImporterTest {

    private class FakeClient(private val result: AiResult<String>) : AiClient {
        override suspend fun extract(inputs: List<AiInput>) = result
    }

    private fun read(result: AiResult<String>) = runBlocking {
        DocumentImporter(FakeClient(result)).read(
            inputs = listOf(AiInput.Text("doc")),
            basis = MaserBasis.Net,
            fallbackCurrency = "ILS",
            knownNames = listOf("Yad Eliezer"),
            today = LocalDate.of(2026, 11, 12)
        )
    }

    @Test
    fun `errors pass through`() {
        assertEquals(AiResult.Error(AiError.QuotaExceeded), read(AiResult.Error(AiError.QuotaExceeded)))
    }

    @Test
    fun `names snap to the stored spelling`() {
        val json = """{"entries":[{"kind":"donation","name":"yad eliezer","amount":180}]}"""
        val entries = (read(AiResult.Success(json)) as AiResult.Success).value
        assertEquals("Yad Eliezer", entries.single().name)
    }
}
