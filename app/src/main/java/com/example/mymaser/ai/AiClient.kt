package com.example.mymaser.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import java.util.concurrent.atomic.AtomicBoolean
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ServerException
import kotlinx.coroutines.delay
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Something to read: free text, or a file such as a photo or a PDF. */
sealed interface AiInput {
    data class Text(val text: String) : AiInput
    class File(val bytes: ByteArray, val mimeType: String) : AiInput
}

sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data class Error(val reason: AiError) : AiResult<Nothing>
}

enum class AiError {
    /** google-services.json is missing, so Firebase never started. */
    NotConfigured,

    /** The user hasn't turned on AI features in Settings. */
    Disabled,
    QuotaExceeded,
    Network,

    /** Gemini is overloaded right now; trying again shortly usually works. */
    Busy,
    Failed
}

interface AiClient {
    /** The model's raw JSON reply for [inputs], following [EXTRACTION_PROMPT]. */
    suspend fun extract(inputs: List<AiInput>): AiResult<String>
}

/**
 * Free-tier models; the aliases follow Google's current releases. Each has its own daily quota and
 * load, and Flash's free quota is small (about 20 requests a day), so callers list them in order of
 * preference and fall back to the other when one is busy or used up.
 */
internal const val GEMINI_FLASH = "gemini-flash-latest"
internal const val GEMINI_FLASH_LITE = "gemini-flash-lite-latest"

/** Reading documents benefits most from the stronger model. */
internal val DOCUMENT_MODELS = listOf(GEMINI_FLASH, GEMINI_FLASH_LITE)

private const val BUSY_RETRY_DELAY_MS = 1_500L
private val BUSY_HINTS = listOf("high demand", "overloaded", "unavailable", "503", "try again later")

private val appCheckInstalled = AtomicBoolean(false)

/** The Gemini service, or null when google-services.json is missing and Firebase never started. */
internal fun geminiOrNull(context: Context): FirebaseAI? {
    if (FirebaseApp.getApps(context).isEmpty()) return null
    if (appCheckInstalled.compareAndSet(false, true)) installAppCheck()
    return Firebase.ai(backend = GenerativeBackend.googleAI())
}

/** Runs a Gemini call, turning its failures into an [AiError]. */
internal suspend fun <T> geminiCall(block: suspend () -> AiResult<T>): AiResult<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: QuotaExceededException) {
        Log.w(AI_LOG_TAG, "Gemini quota exceeded", e)
        AiResult.Error(AiError.QuotaExceeded)
    } catch (e: ServerException) {
        Log.w(AI_LOG_TAG, "Gemini server error", e)
        val busy = BUSY_HINTS.any { e.message.orEmpty().contains(it, ignoreCase = true) }
        AiResult.Error(if (busy) AiError.Busy else AiError.Failed)
    } catch (e: FirebaseAIException) {
        Log.w(AI_LOG_TAG, "Gemini call failed", e)
        AiResult.Error(if (e.cause is IOException) AiError.Network else AiError.Failed)
    } catch (e: IOException) {
        Log.w(AI_LOG_TAG, "Gemini unreachable", e)
        AiResult.Error(AiError.Network)
    }

internal const val AI_LOG_TAG = "MyMaserAi"

private val FALL_BACK_ON = setOf(AiError.Busy, AiError.QuotaExceeded)

/** Runs [block] on each of [models] in turn while Gemini reports it's busy or out of quota. */
internal suspend fun <T> geminiWithFallback(
    models: List<GenerativeModel>,
    block: suspend (GenerativeModel) -> AiResult<T>
): AiResult<T> {
    var result: AiResult<T> = AiResult.Error(AiError.Busy)
    models.forEachIndexed { index, model ->
        if (index > 0) {
            Log.i(AI_LOG_TAG, "Gemini model $index of ${models.size} unavailable, trying the next one")
            delay(BUSY_RETRY_DELAY_MS)
        }
        result = geminiCall { block(model) }
        if ((result as? AiResult.Error)?.reason !in FALL_BACK_ON) return result
    }
    return result
}

class GeminiAiClient private constructor(context: Context) : AiClient {

    private val appContext: Context = context.applicationContext

    private val models by lazy {
        val ai = geminiOrNull(appContext) ?: return@lazy emptyList()
        DOCUMENT_MODELS.map { name ->
            ai.generativeModel(
                modelName = name,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = extractionSchema
                },
                systemInstruction = content { text(EXTRACTION_PROMPT) }
            )
        }
    }

    override suspend fun extract(inputs: List<AiInput>): AiResult<String> {
        if (models.isEmpty()) return AiResult.Error(AiError.NotConfigured)
        val request = content {
            inputs.forEach {
                when (it) {
                    is AiInput.Text -> text(it.text)
                    is AiInput.File -> inlineData(it.bytes, it.mimeType)
                }
            }
        }
        return geminiWithFallback(models) { model ->
            val reply = model.generateContent(request).text
            if (reply.isNullOrBlank()) AiResult.Error(AiError.Failed) else AiResult.Success(reply)
        }
    }

    companion object {
        private val extractionSchema: Schema = with(ExtractionFields) {
            Schema.obj(
                mapOf(
                    ENTRIES to Schema.array(
                        Schema.obj(
                            properties = mapOf(
                                KIND to Schema.enumeration(listOf(KIND_INCOME, KIND_DONATION)),
                                NAME to Schema.string("Employer, payer or organisation"),
                                AMOUNT to Schema.double(),
                                GROSS to Schema.double("Payslip gross pay"),
                                NET to Schema.double("Payslip net pay"),
                                CURRENCY to Schema.string("ISO 4217 code"),
                                DATE to Schema.string("YYYY-MM-DD"),
                                KNOWN_NAME to Schema.string("One of the user's listed names, only if clearly the same source")
                            ),
                            optionalProperties = listOf(GROSS, NET, CURRENCY, DATE, KNOWN_NAME)
                        )
                    )
                )
            )
        }

        @Volatile
        private var instance: GeminiAiClient? = null

        fun getInstance(context: Context): GeminiAiClient =
            instance ?: synchronized(this) {
                instance ?: GeminiAiClient(context).also { instance = it }
            }
    }
}
