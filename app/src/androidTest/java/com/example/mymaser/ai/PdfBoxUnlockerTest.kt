package com.example.mymaser.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBoxUnlockerTest {

    private val unlocker = PdfBoxUnlocker(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun payslip(userPassword: String?): ByteArray {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText("Acme Ltd payslip 10/2026  Gross 16,450.00  Net 12,880.50")
                stream.endText()
            }
            if (userPassword != null) {
                document.protect(StandardProtectionPolicy("owner-pw", userPassword, AccessPermission()))
            }
            return ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
    }

    @Test
    fun aPlainPdfIsLeftAsAFile() {
        assertEquals(PdfOpen.NotLocked, unlocker.open(payslip(null), listOf("123456789")))
    }

    @Test
    fun aLockedPdfOpensOnlyWithItsPassword() {
        val locked = payslip("123456789")
        assertEquals(PdfOpen.Locked, unlocker.open(locked, emptyList()))
        assertEquals(PdfOpen.Locked, unlocker.open(locked, listOf("000000000")))
        val opened = unlocker.open(locked, listOf("000000000", "123456789"))
        assertTrue(opened is PdfOpen.Text && opened.text.contains("Net 12,880.50"))
    }
}
