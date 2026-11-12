package com.example.mymaser.gmail

import android.net.Uri
import android.util.Base64
import com.example.mymaser.ai.MAX_FILE_BYTES
import com.example.mymaser.ai.MIME_PDF
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** The access token was rejected; the user has to connect Gmail again. */
class GmailUnauthorizedException : IOException("Gmail rejected the access token")

/** A message's subject and readable text, plus where its PDF attachments are. */
data class GmailMessage(
    val id: String,
    val subject: String,
    val text: String,
    val pdfs: List<PdfRef>
) {
    /** [data] when the PDF came inline, otherwise fetched with [attachmentId]. */
    data class PdfRef(val attachmentId: String?, val data: String?, val size: Int)
}

/** Words searched for by default; the user can change them in Settings. */
const val DEFAULT_GMAIL_KEYWORDS = "תלוש, קבלה, תרומה, payslip, receipt, donation"

private const val SEARCH_WINDOW = "newer_than:35d"
private const val MAX_TEXT_CHARS = 20_000

/** A Gmail search for recent mail containing any of the comma-separated [keywords]. */
fun buildGmailQuery(keywords: String): String {
    val words = keywords.split(',').map { it.trim().replace("\"", "") }.filter { it.isNotEmpty() }
        .ifEmpty { DEFAULT_GMAIL_KEYWORDS.split(',').map { it.trim() } }
    val any = words.joinToString(" OR ") { if (' ' in it) "\"$it\"" else it }
    return "$SEARCH_WINDOW ($any)"
}

fun parseMessageIds(json: String): List<String> {
    val messages = JSONObject(json).optJSONArray("messages") ?: return emptyList()
    return (0 until messages.length()).mapNotNull { messages.optJSONObject(it)?.optString("id") }
        .filter { it.isNotEmpty() }
}

/** Reads a `format=full` message: the subject, the plain text (or HTML as text), and PDF parts. */
fun parseMessage(json: String, decode: (String) -> String): GmailMessage {
    val root = JSONObject(json)
    val payload = root.optJSONObject("payload") ?: JSONObject()
    val subject = payload.optJSONArray("headers").objects()
        .firstOrNull { it.optString("name").equals("Subject", ignoreCase = true) }
        ?.optString("value").orEmpty()

    val parts = mutableListOf<JSONObject>()
    fun collect(part: JSONObject) {
        parts += part
        part.optJSONArray("parts").objects().forEach(::collect)
    }
    collect(payload)

    fun bodyOf(mimeType: String) = parts
        .filter { it.optString("mimeType") == mimeType && it.optString("filename").isEmpty() }
        .mapNotNull { it.optJSONObject("body")?.optString("data")?.takeIf(String::isNotEmpty) }
        .joinToString("\n") { decode(it) }

    val text = bodyOf("text/plain").ifBlank { htmlToText(bodyOf("text/html")) }
    val pdfs = parts.filter { part ->
        part.optString("mimeType") == MIME_PDF || part.optString("filename").endsWith(".pdf", ignoreCase = true)
    }.mapNotNull { part ->
        val body = part.optJSONObject("body") ?: return@mapNotNull null
        GmailMessage.PdfRef(
            attachmentId = body.optString("attachmentId").takeIf { it.isNotEmpty() },
            data = body.optString("data").takeIf { it.isNotEmpty() },
            size = body.optInt("size")
        )
    }
    return GmailMessage(root.optString("id"), subject, text.take(MAX_TEXT_CHARS), pdfs)
}

/** HTML mail as plain text: drops style/script blocks and tags, decodes a few entities. */
fun htmlToText(html: String): String = html
    .replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), " ")
    .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>"), "\n")
    .replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")
    .lines()
    .map { it.replace(Regex("\\s+"), " ").trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** Gmail's REST API, read-only, over plain HTTPS like the exchange-rate download. */
class GmailApi(private val token: String) : GmailSource {

    override suspend fun search(query: String): List<String> =
        parseMessageIds(get("messages?maxResults=$MAX_RESULTS&q=${Uri.encode(query)}"))

    override suspend fun message(id: String): GmailMessage =
        parseMessage(get("messages/$id?format=full")) { String(decodeBase64Url(it), Charsets.UTF_8) }

    /** The PDF's bytes, or null when it's bigger than the AI can take. */
    override suspend fun pdf(messageId: String, ref: GmailMessage.PdfRef): ByteArray? {
        if (ref.size > MAX_FILE_BYTES) return null
        val data = ref.data ?: ref.attachmentId?.let { attachmentId ->
            JSONObject(get("messages/$messageId/attachments/$attachmentId")).optString("data")
        } ?: return null
        return decodeBase64Url(data).takeIf { it.size <= MAX_FILE_BYTES }
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw GmailUnauthorizedException()
                else -> throw IOException("Gmail answered $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/"
        const val MAX_RESULTS = 20
        const val TIMEOUT_MS = 20_000

        fun decodeBase64Url(data: String): ByteArray = Base64.decode(data, Base64.URL_SAFE)
    }
}
