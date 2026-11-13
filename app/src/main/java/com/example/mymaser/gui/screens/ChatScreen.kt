package com.example.mymaser.gui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.ai.AiError
import com.example.mymaser.ai.ChatMessage
import com.example.mymaser.ai.canRetry
import com.example.mymaser.gui.components.EmptyState
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.FilledTonalButton

private const val INTRO_KEY = "intro"
private const val THINKING_KEY = "thinking"

/** Ask questions about your own records; answers come from totals computed on the phone. */
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    thinking: Boolean,
    onAsk: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    enabled: Boolean = true
) {
    if (!enabled) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.Chat,
                message = stringResource(R.string.chat_needs_ai),
                action = {
                    FilledTonalButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                }
            )
        }
        return
    }
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun ask(question: String) {
        if (question.isBlank() || thinking) return
        onAsk(question)
        text = ""
    }

    LaunchedEffect(messages.size, thinking) {
        val last = messages.size + (if (thinking) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = INTRO_KEY) { Box(Modifier.animateItem()) { Intro(onAsk = ::ask) } }
            }
            itemsIndexed(messages, key = { index, _ -> index }) { index, message ->
                Box(Modifier.fillMaxWidth().animateItem()) {
                    when (message) {
                        is ChatMessage.Question -> Bubble(message.text, mine = true)
                        is ChatMessage.Answer -> Bubble(message.text, mine = false)
                        is ChatMessage.Problem -> Problem(
                            error = message.error,
                            canRetry = index == messages.lastIndex && !thinking,
                            onRetry = onRetry,
                            onOpenSettings = onOpenSettings
                        )
                    }
                }
            }
            if (thinking) {
                item(key = THINKING_KEY) {
                    Row(
                        modifier = Modifier.animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.chat_thinking), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(stringResource(R.string.chat_hint)) },
            shape = MaterialTheme.shapes.small,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { ask(text) }),
            trailingIcon = {
                IconButton(onClick = { ask(text) }, enabled = text.isNotBlank() && !thinking) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                }
            }
        )
    }
}

@Composable
private fun Intro(onAsk: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.chat_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.chat_suggestion_year, R.string.chat_suggestion_balance, R.string.chat_suggestion_month)
                .forEach { res ->
                    val suggestion = stringResource(res)
                    SuggestionChip(onClick = { onAsk(suggestion) }, label = { Text(suggestion) })
                }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Problem(error: AiError, canRetry: Boolean, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val message = when (error) {
        AiError.Disabled -> R.string.import_error_disabled
        AiError.NotConfigured -> R.string.import_error_not_configured
        AiError.QuotaExceeded -> R.string.import_error_quota
        AiError.Network -> R.string.import_error_network
        AiError.Busy -> R.string.ai_error_busy
        AiError.Failed -> R.string.chat_error_failed
    }
    Column {
        Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (error == AiError.Disabled) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
        } else if (canRetry && error.canRetry) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
