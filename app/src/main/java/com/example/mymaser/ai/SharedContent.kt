package com.example.mymaser.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gemini accepts up to 20 MB inline; this leaves room for the prompt and base64. */
const val MAX_FILE_BYTES = 15 * 1024 * 1024

/** What could be read from a share or a picker, and how many items were left out. */
data class ReadDocuments(val inputs: List<AiInput>, val skipped: Int)

fun isSupportedMimeType(mimeType: String?): Boolean =
    mimeType != null && (mimeType.startsWith("image/") || mimeType == MIME_PDF)

const val MIME_PDF = "application/pdf"

fun Intent.isDocumentShare(): Boolean =
    action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

/** The text and files another app shared, read off the main thread. */
suspend fun readShare(context: Context, intent: Intent): ReadDocuments {
    val text = listOfNotNull(
        intent.getStringExtra(Intent.EXTRA_SUBJECT),
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    ).filter { it.isNotBlank() }.joinToString("\n")
    val uris = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }
    val files = readFiles(context, uris, fallbackMimeType = intent.type)
    val textInput = text.takeIf { it.isNotEmpty() }?.let { AiInput.Text(it) }
    return files.copy(inputs = listOfNotNull(textInput) + files.inputs)
}

/** Reads picked or scanned files; unsupported or oversized ones are counted as skipped. */
suspend fun readFiles(
    context: Context,
    uris: List<Uri>,
    fallbackMimeType: String? = null
): ReadDocuments = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val inputs = uris.mapNotNull { uri ->
        val mimeType = resolver.getType(uri) ?: fallbackMimeType
        if (mimeType == null || !isSupportedMimeType(mimeType)) return@mapNotNull null
        val bytes = try {
            resolver.openInputStream(uri)?.use { readAtMost(it, MAX_FILE_BYTES) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        bytes?.let { AiInput.File(it, mimeType) }
    }
    ReadDocuments(inputs, skipped = uris.size - inputs.size)
}

/** The stream's bytes, or null when it holds more than [limit]. */
fun readAtMost(stream: InputStream, limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) return null
        out.write(buffer, 0, read)
    }
}
