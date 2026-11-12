package com.example.mymaser.gui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.example.mymaser.R

/** One line to add an entry by typing or saying it, e.g. "180 to Yad Eliezer". */
@Composable
fun QuickAddField(onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val canListen = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var text by rememberSaveable { mutableStateOf("") }
    val hint = stringResource(R.string.quick_add_hint)

    fun submit(note: String) {
        if (note.isBlank()) return
        focusManager.clearFocus()
        onSubmit(note)
        text = ""
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::submit)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit(text) }),
        trailingIcon = {
            Row {
                if (text.isNotBlank()) {
                    IconButton(onClick = { submit(text) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.quick_add_send))
                    }
                } else if (canListen) {
                    IconButton(onClick = {
                        speech.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, hint)
                        )
                    }) {
                        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.quick_add_voice))
                    }
                }
            }
        }
    )
}
