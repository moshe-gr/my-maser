package com.example.mymaser.ai

import android.content.Context
import com.example.mymaser.settings.pdfPasswords
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What opening a PDF on the phone gave. */
sealed interface PdfOpen {
    data object NotLocked : PdfOpen
    data object Locked : PdfOpen

    /** Opened with a password but holds no text, e.g. a scan. */
    data object NoText : PdfOpen
    data class Text(val text: String) : PdfOpen
}

fun interface PdfUnlocker {
    fun open(bytes: ByteArray, passwords: List<String>): PdfOpen
}

/** Inputs ready to send, with locked PDFs replaced by their redacted text or left out. */
data class Unlocked(val inputs: List<AiInput>, val locked: Int, val unreadable: Int)

private const val MAX_PDF_TEXT_CHARS = 20_000
private const val MIN_PASSWORD_LENGTH_TO_REDACT = 4

/** Labels of lines that hold the employee's ID or address, as written and as PDF text reverses them. */
private val SENSITIVE_LABELS = listOf("ת.ז", "ת\"ז", "ת״ז", "תעודת זהות", "מספר זהות", "כתובת")
    .flatMap { listOf(it, it.reversed()) } + listOf("address", "id no", "id number")

/** Seven or more digits, maybe dash-separated: ID, bank account and phone numbers, never amounts. */
private val LONG_NUMBER = Regex("\\d(?:-?\\d){6,}")

/**
 * A locked PDF's text with what identifies the person removed: the password itself, long numbers,
 * and lines labelled as ID or address.
 */
fun redactPdfText(text: String, passwords: List<String>): String {
    var result = text
    passwords.filter { it.length >= MIN_PASSWORD_LENGTH_TO_REDACT }.forEach { result = result.replace(it, "•••") }
    return result.lines()
        .filterNot { line -> SENSITIVE_LABELS.any { line.contains(it, ignoreCase = true) } }
        .joinToString("\n") { it.replace(LONG_NUMBER, "•••") }
        .take(MAX_PDF_TEXT_CHARS)
}

/** Opens locked PDFs among [inputs]; other inputs pass through unchanged. */
fun unlockPdfs(inputs: List<AiInput>, unlocker: PdfUnlocker, passwords: List<String>): Unlocked {
    var locked = 0
    var unreadable = 0
    val opened = inputs.mapNotNull { input ->
        if (input !is AiInput.File || input.mimeType != MIME_PDF) return@mapNotNull input
        when (val result = unlocker.open(input.bytes, passwords)) {
            PdfOpen.NotLocked -> input
            PdfOpen.Locked -> null.also { locked++ }
            PdfOpen.NoText -> null.also { unreadable++ }
            is PdfOpen.Text -> AiInput.Text(LOCKED_PDF_HEADER + redactPdfText(result.text, passwords))
        }
    }
    return Unlocked(opened, locked, unreadable)
}

private const val LOCKED_PDF_HEADER = "Text of a password-protected PDF, read on the user's phone:\n"

/** Reads locked PDFs with PDFBox, so only their text, never the file, is sent on. */
class PdfBoxUnlocker(context: Context) : PdfUnlocker {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    override fun open(bytes: ByteArray, passwords: List<String>): PdfOpen {
        try {
            PDDocument.load(bytes).close()
            return PdfOpen.NotLocked
        } catch (_: InvalidPasswordException) {
            // Needs a password; tried below.
        } catch (_: IOException) {
            return PdfOpen.NotLocked
        }
        for (password in passwords) {
            val text = try {
                PDDocument.load(bytes, password).use { document ->
                    PDFTextStripper().apply { sortByPosition = true }.getText(document)
                }
            } catch (_: IOException) {
                continue
            }
            return if (text.isBlank()) PdfOpen.NoText else PdfOpen.Text(text)
        }
        return PdfOpen.Locked
    }
}

/** Opens locked PDFs with the passwords saved in Settings, off the main thread. */
class LockedPdfOpener(private val context: Context) {

    private val unlocker by lazy { PdfBoxUnlocker(context) }

    suspend fun open(inputs: List<AiInput>): Unlocked {
        if (inputs.none { it is AiInput.File && it.mimeType == MIME_PDF }) return Unlocked(inputs, 0, 0)
        val passwords = context.pdfPasswords()
        return withContext(Dispatchers.Default) { unlockPdfs(inputs, unlocker, passwords) }
    }
}
