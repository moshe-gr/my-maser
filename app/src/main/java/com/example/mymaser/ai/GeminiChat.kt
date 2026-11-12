package com.example.mymaser.ai

import android.content.Context
import android.util.Log
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A conversation about the user's records; [ChatData] is read fresh for every question. */
interface AiChat {
    suspend fun send(question: String, data: ChatData): AiResult<String>
}

/**
 * Gemini answers by calling [ChatTools] on the phone, so only the totals it asks for are sent,
 * never the raw list of entries. The conversation lives only as long as this object.
 */
class GeminiChat(context: Context) : AiChat {

    private val appContext = context.applicationContext
    private val history = mutableListOf<Content>()

    private val models by lazy {
        val ai = geminiOrNull(appContext) ?: return@lazy emptyList()
        CHAT_MODELS.map { name ->
            ai.generativeModel(
                modelName = name,
                tools = listOf(Tool.functionDeclarations(ChatTools.all.map { it.toDeclaration() })),
                systemInstruction = content { text(ChatTools.SYSTEM_PROMPT) }
            )
        }
    }

    override suspend fun send(question: String, data: ChatData): AiResult<String> {
        if (models.isEmpty()) return AiResult.Error(AiError.NotConfigured)
        return geminiWithFallback(models) { model ->
            val turn = mutableListOf(content { text(ChatTools.framedQuestion(question, data.now.toLocalDate())) })
            repeat(MAX_TOOL_ROUNDS) { round ->
                val started = System.currentTimeMillis()
                val response = model.generateContent(history + turn)
                val candidate = response.candidates.firstOrNull()
                Log.d(
                    AI_LOG_TAG,
                    "chat round $round: ${System.currentTimeMillis() - started} ms, " +
                        "finish=${candidate?.finishReason?.name}, " +
                        "parts=${candidate?.content?.parts?.map { it::class.simpleName }}"
                )
                val reply = candidate?.content ?: return@geminiWithFallback AiResult.Error(AiError.Failed)
                turn += reply
                val calls = reply.parts.filterIsInstance<FunctionCallPart>()
                if (calls.isEmpty()) {
                    val answer = ChatTools.cleanAnswer(
                        reply.parts.filterIsInstance<TextPart>().filterNot { it.isThought }.joinToString("") { it.text }
                    )
                    if (answer.isEmpty()) {
                        Log.w(AI_LOG_TAG, "chat reply had no text")
                        return@geminiWithFallback AiResult.Error(AiError.Failed)
                    }
                    history += turn
                    return@geminiWithFallback AiResult.Success(answer)
                }
                turn += content("user") {
                    calls.forEach { call ->
                        val args = call.args.mapValues { (_, value) -> value.asText() }
                        val result = ChatTools.run(call.name, args, data)
                        Log.d(AI_LOG_TAG, "tool ${call.name}(${args.keys}) -> error=${result["error"]}")
                        part(FunctionResponsePart(call.name, result.toJsonObject(), call.id))
                    }
                }
            }
            Log.w(AI_LOG_TAG, "chat gave up after $MAX_TOOL_ROUNDS tool rounds")
            AiResult.Error(AiError.Failed)
        }
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 5

        /** Answers are simple sums from the tools, so the lighter model with the larger free quota leads. */
        val CHAT_MODELS = listOf(GEMINI_FLASH_LITE, GEMINI_FLASH)

        fun ChatTool.toDeclaration() = FunctionDeclaration(
            name = name,
            description = description,
            parameters = parameters.mapValues { (_, description) -> Schema.string(description) },
            optionalParameters = optional
        )

        fun JsonElement.asText(): String? = when (this) {
            is JsonPrimitive -> contentOrNull
            JsonNull -> null
            else -> toString()
        }

        fun Map<*, *>.toJsonObject(): JsonObject =
            JsonObject(entries.associate { (key, value) -> key.toString() to value.toJson() })

        fun Any?.toJson(): JsonElement = when (this) {
            null -> JsonNull
            is Number -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Map<*, *> -> toJsonObject()
            is List<*> -> JsonArray(map { it.toJson() })
            else -> JsonPrimitive(toString())
        }
    }
}
