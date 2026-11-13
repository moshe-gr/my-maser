package com.example.mymaser.gmail

import com.example.mymaser.ai.AiClient
import com.example.mymaser.ai.AiError
import com.example.mymaser.ai.AiInput
import com.example.mymaser.ai.AiResult
import com.example.mymaser.ai.DocumentImporter
import com.example.mymaser.ai.ExtractedEntry
import com.example.mymaser.ai.KnownNames
import com.example.mymaser.ai.Unlocked
import com.example.mymaser.ai.entriesFromJson
import com.example.mymaser.ai.entriesToJson
import com.example.mymaser.settings.MaserBasis
import java.io.IOException
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailTest {

    private fun encode(text: String) = Base64.getUrlEncoder().encodeToString(text.toByteArray())
    private val decode: (String) -> String = { String(Base64.getUrlDecoder().decode(it)) }

    @Test
    fun `query searches recent mail for any keyword`() {
        assertEquals("newer_than:35d (תלוש OR \"pay slip\" OR receipt)", buildGmailQuery("תלוש, pay slip ,receipt,"))
        assertTrue(buildGmailQuery(" , ").contains("payslip"))
    }

    @Test
    fun `message ids`() {
        assertEquals(listOf("a", "b"), parseMessageIds("""{"messages":[{"id":"a"},{"id":"b"}]}"""))
        assertEquals(emptyList<String>(), parseMessageIds("""{"resultSizeEstimate":0}"""))
    }

    @Test
    fun `message subject, plain text and pdf parts`() {
        val json = """
            {"id":"m1","payload":{"mimeType":"multipart/mixed",
              "headers":[{"name":"Subject","value":"תלוש שכר אוקטובר"}],
              "parts":[
                {"mimeType":"multipart/alternative","parts":[
                  {"mimeType":"text/plain","filename":"","body":{"data":"${encode("Net pay 9,000")}"}},
                  {"mimeType":"text/html","filename":"","body":{"data":"${encode("<b>ignored</b>")}"}}]},
                {"mimeType":"application/pdf","filename":"slip.pdf","body":{"attachmentId":"att1","size":1234}}]}}
        """
        val message = parseMessage(json, decode)
        assertEquals("תלוש שכר אוקטובר", message.subject)
        assertEquals("Net pay 9,000", message.text)
        assertEquals(listOf(GmailMessage.PdfRef("att1", null, 1234)), message.pdfs)
    }

    @Test
    fun `html only mail becomes text`() {
        val json = """{"id":"m2","payload":{"mimeType":"text/html","body":{"data":"${encode(
            "<style>p{}</style><p>Thank you for your donation</p><p>Total: 180&nbsp;₪</p>"
        )}"}}}"""
        assertEquals("Thank you for your donation\nTotal: 180 ₪", parseMessage(json, decode).text)
    }

    @Test
    fun `entries survive storage`() {
        val entries = listOf(
            ExtractedEntry(false, "Acme", 9000F, "ILS", LocalDate.of(2026, 10, 31)),
            ExtractedEntry(true, "יד אליעזר", 180F, "USD", null, knownName = "Yad Eliezer")
        )
        assertEquals(entries, entriesFromJson(entriesToJson(entries)))
    }

    private class FakeSource(private val messages: Map<String, GmailMessage>) : GmailSource {
        override suspend fun search(query: String) = messages.keys.toList()
        override suspend fun message(id: String) = messages.getValue(id)
        override suspend fun pdf(messageId: String, ref: GmailMessage.PdfRef): ByteArray? = byteArrayOf(1)
    }

    private class FakeStore(known: List<String> = emptyList()) : PendingImportStore {
        val items = known.associateWith { PendingImport(it, "", "[]", 0, PendingImport.DONE) }.toMutableMap()
        override suspend fun knownIds(ids: List<String>) = ids.filter { it in items }
        override suspend fun insert(item: PendingImport) {
            items.putIfAbsent(item.messageId, item)
        }
    }

    private class ScriptedClient(private val replies: Map<String, AiResult<String>>) : AiClient {
        override suspend fun extract(inputs: List<AiInput>): AiResult<String> {
            val text = (inputs.first() as AiInput.Text).text
            return replies.entries.first { text.contains(it.key) }.value
        }
    }

    private fun message(id: String, subject: String) = GmailMessage(id, subject, "body", emptyList())

    private fun run(source: GmailSource, store: FakeStore, client: AiClient) = runBlocking {
        GmailImporter(store, DocumentImporter(client) { true })
            .run(source, "q", MaserBasis.Net, "ILS", KnownNames(emptyList(), emptyList()), nowMillis = 1)
    }

    @Test
    fun `new emails with entries wait for review, others are remembered as done`() {
        val source = FakeSource(
            mapOf("1" to message("1", "slip"), "2" to message("2", "newsletter"), "3" to message("3", "old"))
        )
        val store = FakeStore(known = listOf("3"))
        val client = ScriptedClient(
            mapOf(
                "slip" to AiResult.Success("""{"entries":[{"kind":"income","name":"Acme","amount":9000}]}"""),
                "newsletter" to AiResult.Success("""{"entries":[]}""")
            )
        )
        assertEquals(1, run(source, store, client))
        assertEquals(PendingImport.PENDING, store.items.getValue("1").status)
        assertEquals("Acme", entriesFromJson(store.items.getValue("1").entriesJson).single().name)
        assertEquals(PendingImport.DONE, store.items.getValue("2").status)
    }

    @Test
    fun `quota stops the run without remembering the email`() {
        val store = FakeStore()
        val client = ScriptedClient(mapOf("slip" to AiResult.Error(AiError.QuotaExceeded)))
        assertEquals(0, run(FakeSource(mapOf("1" to message("1", "slip"))), store, client))
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `an email whose PDF can't be opened yet is left for a later run`() {
        val store = FakeStore()
        val client = ScriptedClient(mapOf("slip" to AiResult.Success("""{"entries":[]}""")))
        val source = FakeSource(
            mapOf("1" to GmailMessage("1", "slip", "body", listOf(GmailMessage.PdfRef(null, "AQ", 1))))
        )
        val found = runBlocking {
            GmailImporter(store, DocumentImporter(client) { true }) { Unlocked(emptyList(), locked = 1, unreadable = 0) }
                .run(source, "q", MaserBasis.Net, "ILS", KnownNames(emptyList(), emptyList()), nowMillis = 1)
        }
        assertEquals(0, found)
        assertTrue(store.items.isEmpty())
    }

    @Test(expected = IOException::class)
    fun `network failure is thrown so the worker retries`() {
        val client = ScriptedClient(mapOf("slip" to AiResult.Error(AiError.Network)))
        run(FakeSource(mapOf("1" to message("1", "slip"))), FakeStore(), client)
    }
}
