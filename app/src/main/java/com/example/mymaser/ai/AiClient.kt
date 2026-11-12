package com.example.mymaser.ai

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.QuotaExceededException
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
    QuotaExceeded,
    Network,
    Failed
}

interface AiClient {
    /** The model's raw JSON reply for [inputs], following [EXTRACTION_PROMPT]. */
    suspend fun extract(inputs: List<AiInput>): AiResult<String>
}

class GeminiAiClient private constructor(private val context: Context) : AiClient {

    private val model by lazy {
        installAppCheck()
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
            },
            systemInstruction = content { text(EXTRACTION_PROMPT) }
        )
    }

    override suspend fun extract(inputs: List<AiInput>): AiResult<String> {
        if (FirebaseApp.getApps(context).isEmpty()) return AiResult.Error(AiError.NotConfigured)
        val request = content {
            inputs.forEach {
                when (it) {
                    is AiInput.Text -> text(it.text)
                    is AiInput.File -> inlineData(it.bytes, it.mimeType)
                }
            }
        }
        return try {
            val reply = model.generateContent(request).text
            if (reply.isNullOrBlank()) AiResult.Error(AiError.Failed) else AiResult.Success(reply)
        } catch (e: CancellationException) {
            throw e
        } catch (_: QuotaExceededException) {
            AiResult.Error(AiError.QuotaExceeded)
        } catch (e: FirebaseAIException) {
            AiResult.Error(if (e.cause is IOException) AiError.Network else AiError.Failed)
        } catch (_: IOException) {
            AiResult.Error(AiError.Network)
        }
    }

    companion object {
        /** Free-tier Flash model; the alias follows Google's current release. */
        private const val MODEL_NAME = "gemini-flash-latest"

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
                                DATE to Schema.string("YYYY-MM-DD")
                            ),
                            optionalProperties = listOf(GROSS, NET, CURRENCY, DATE)
                        )
                    )
                )
            )
        }

        @Volatile
        private var instance: GeminiAiClient? = null

        fun getInstance(context: Context): GeminiAiClient =
            instance ?: synchronized(this) {
                instance ?: GeminiAiClient(context.applicationContext).also { instance = it }
            }
    }
}
