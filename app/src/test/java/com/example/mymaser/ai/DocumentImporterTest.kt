package com.example.mymaser.ai

import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DocumentImporterTest {

    private class FakeClient(private val result: AiResult<String>) : AiClient {
        var called = false
        override suspend fun extract(inputs: List<AiInput>): AiResult<String> {
            called = true
            return result
        }
    }

    private fun read(client: AiClient, enabled: Boolean = true) = runBlocking {
        DocumentImporter(client, isEnabled = { enabled }).read(
            inputs = listOf(AiInput.Text("doc")),
            basis = MaserBasis.Net,
            fallbackCurrency = "ILS",
            knownNames = listOf("Yad Eliezer"),
            today = LocalDate.of(2026, 11, 12)
        )
    }

    @Test
    fun `nothing is sent while AI is off`() {
        val client = FakeClient(AiResult.Success("""{"entries":[]}"""))
        assertEquals(AiResult.Error(AiError.Disabled), read(client, enabled = false))
        assertFalse(client.called)
    }

    @Test
    fun `errors pass through`() {
        val client = FakeClient(AiResult.Error(AiError.QuotaExceeded))
        assertEquals(AiResult.Error(AiError.QuotaExceeded), read(client))
    }

    @Test
    fun `an unreadable reply is a failure, not an empty document`() {
        assertEquals(AiResult.Error(AiError.Failed), read(FakeClient(AiResult.Success("{\"entr"))))
        assertEquals(AiResult.Success(emptyList<ExtractedEntry>()), read(FakeClient(AiResult.Success("""{"entries":[]}"""))))
    }

    @Test
    fun `names snap to the stored spelling`() {
        val json = """{"entries":[{"kind":"donation","name":"yad eliezer","amount":180}]}"""
        val entries = (read(FakeClient(AiResult.Success(json))) as AiResult.Success).value
        assertEquals("Yad Eliezer", entries.single().name)
    }
}
