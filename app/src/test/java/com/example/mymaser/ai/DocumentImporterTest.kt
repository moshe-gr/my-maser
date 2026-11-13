package com.example.mymaser.ai

import com.example.mymaser.settings.MaserBasis
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentImporterTest {

    private class FakeClient(private val result: AiResult<String>) : AiClient {
        var called = false
        var sent: List<AiInput> = emptyList()
        override suspend fun extract(inputs: List<AiInput>): AiResult<String> {
            called = true
            sent = inputs
            return result
        }
    }

    private fun read(client: AiClient, enabled: Boolean = true) = runBlocking {
        DocumentImporter(client, isEnabled = { enabled }).read(
            inputs = listOf(AiInput.Text("doc")),
            basis = MaserBasis.Net,
            fallbackCurrency = "ILS",
            knownNames = KnownNames(incomes = listOf("Acme"), donations = listOf("Yad Eliezer")),
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
    fun `known names go first, labelled, and the model's pick is kept apart from the name`() {
        val json = """{"entries":[{"kind":"donation","name":"יד אליעזר ע\"ר","amount":180,"knownName":"Yad Eliezer"}]}"""
        val client = FakeClient(AiResult.Success(json))
        val entry = (read(client) as AiResult.Success).value.single()
        assertEquals("יד אליעזר ע\"ר", entry.name)
        assertEquals("Yad Eliezer", entry.knownName)
        val hint = (client.sent.first() as AiInput.Text).text
        assertTrue(hint.contains("Income: Acme") && hint.contains("Donations: Yad Eliezer"))
        assertEquals(AiInput.Text("doc"), client.sent.last())
    }
}
