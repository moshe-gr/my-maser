package com.example.mymaser.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.settings.aiEnabledFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatMessage {
    data class Question(val text: String) : ChatMessage
    data class Answer(val text: String) : ChatMessage
    data class Problem(val error: AiError) : ChatMessage
}

/** Questions about the user's own records; nothing here is saved. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val chat: AiChat = GeminiChat(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    /** Asks the last question again after it failed. */
    fun retry(data: ChatData?) {
        val messages = _messages.value
        val question = messages.lastOrNull { it is ChatMessage.Question } as? ChatMessage.Question ?: return
        if (messages.lastOrNull() !is ChatMessage.Problem || _thinking.value) return
        _messages.value = messages.dropLast(1)
        send(question.text, data)
    }

    fun ask(question: String, data: ChatData?) {
        val text = question.trim()
        if (text.isEmpty() || _thinking.value) return
        _messages.update { it + ChatMessage.Question(text) }
        send(text, data)
    }

    private fun send(text: String, data: ChatData?) {
        _thinking.value = true
        viewModelScope.launch {
            val result = when {
                !app.aiEnabledFlow().first() -> AiResult.Error(AiError.Disabled)
                data == null -> AiResult.Error(AiError.Failed)
                else -> chat.send(text, data)
            }
            val reply = when (result) {
                is AiResult.Success -> ChatMessage.Answer(result.value)
                is AiResult.Error -> ChatMessage.Problem(result.reason)
            }
            _messages.update { it + reply }
            _thinking.value = false
        }
    }
}
