package com.example.mymaser.ai

import android.content.Context
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

    private val model by lazy {
        geminiOrNull(appContext)?.generativeModel(
            modelName = GEMINI_MODEL,
            tools = listOf(Tool.functionDeclarations(ChatTools.all.map { it.toDeclaration() })),
            systemInstruction = content { text(ChatTools.SYSTEM_PROMPT) }
        )
    }

    override suspend fun send(question: String, data: ChatData): AiResult<String> {
        val model = model ?: return AiResult.Error(AiError.NotConfigured)
        return geminiCall {
            val turn = mutableListOf(content { text("Today is ${data.now.toLocalDate()}. $question") })
            repeat(MAX_TOOL_ROUNDS) {
                val reply = model.generateContent(history + turn).candidates.firstOrNull()?.content
                    ?: return@geminiCall AiResult.Error(AiError.Failed)
                turn += reply
                val calls = reply.parts.filterIsInstance<FunctionCallPart>()
                if (calls.isEmpty()) {
                    val answer = reply.parts.filterIsInstance<TextPart>()
                        .filterNot { it.isThought }.joinToString("") { it.text }.trim()
                    if (answer.isEmpty()) return@geminiCall AiResult.Error(AiError.Failed)
                    history += turn
                    return@geminiCall AiResult.Success(answer)
                }
                turn += content("function") {
                    calls.forEach { call ->
                        val args = call.args.mapValues { (_, value) -> value.asText() }
                        val result = ChatTools.run(call.name, args, data)
                        part(FunctionResponsePart(call.name, result.toJsonObject(), call.id))
                    }
                }
            }
            AiResult.Error(AiError.Failed)
        }
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 5

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
