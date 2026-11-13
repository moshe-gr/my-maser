package com.example.mymaser.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedPdfTest {

    @Test
    fun `identifying details are removed, pay figures stay`() {
        val text = """
            Acme Ltd   תלוש שכר לחודש 10/2026
            ת.ז 123456789
            כתובת: הרצל 5 ירושלים
            בנק 12 סניף 345 חשבון 12-345-678901
            ברוטו 16,450.00   נטו לתשלום 12,880.50
            9000 1200
        """.trimIndent()
        val redacted = redactPdfText(text, passwords = listOf("123456789"))
        assertFalse(redacted.contains("123456789"))
        assertFalse(redacted.contains("הרצל"))
        assertFalse(redacted.contains("678901"))
        assertTrue(redacted.contains("Acme Ltd"))
        assertTrue(redacted.contains("ברוטו 16,450.00   נטו לתשלום 12,880.50"))
        assertTrue(redacted.contains("10/2026"))
        assertTrue(redacted.contains("9000 1200"))
    }

    @Test
    fun `labels reversed by PDF text extraction are recognised too`() {
        assertFalse(redactPdfText("תבותכ: הרצל 5", emptyList()).contains("הרצל"))
    }

    @Test
    fun `locked PDFs become redacted text, unopened ones are counted, others pass through`() {
        val open = AiInput.File(byteArrayOf(1), MIME_PDF)
        val secret = AiInput.File(byteArrayOf(2), MIME_PDF)
        val stuck = AiInput.File(byteArrayOf(3), MIME_PDF)
        val scan = AiInput.File(byteArrayOf(4), MIME_PDF)
        val photo = AiInput.File(byteArrayOf(2), "image/jpeg")
        val note = AiInput.Text("note")
        val unlocker = PdfUnlocker { bytes, passwords ->
            when (bytes.single().toInt()) {
                1 -> PdfOpen.NotLocked
                2 -> if ("pw12" in passwords) PdfOpen.Text("נטו 100 pw12") else PdfOpen.Locked
                3 -> PdfOpen.Locked
                else -> PdfOpen.NoText
            }
        }

        val result = unlockPdfs(listOf(note, open, secret, stuck, scan, photo), unlocker, listOf("pw12"))

        assertEquals(1, result.locked)
        assertEquals(1, result.unreadable)
        assertEquals(4, result.inputs.size)
        assertEquals(note, result.inputs[0])
        assertEquals(open, result.inputs[1])
        val text = (result.inputs[2] as AiInput.Text).text
        assertTrue(text.contains("נטו 100") && !text.contains("pw12"))
        assertEquals(photo, result.inputs[3])
    }
}
