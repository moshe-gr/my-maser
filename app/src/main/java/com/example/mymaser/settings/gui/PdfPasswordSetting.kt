package com.example.mymaser.settings.gui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.mymaser.R

/** The password for locked payslips; never shown again once saved, only replaced or removed. */
@Composable
fun PdfPasswordSetting(
    saved: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    fun save() {
        onSave(password)
        password = ""
    }

    if (saved) {
        SettingRow(
            title = stringResource(R.string.pdf_password_title),
            subtitle = stringResource(R.string.pdf_password_saved)
        ) {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.pdf_password_remove)) }
        }
    } else {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.pdf_password_title)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) save() }),
            trailingIcon = {
                if (password.isNotEmpty()) {
                    TextButton(onClick = ::save) { Text(stringResource(R.string.save)) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        )
    }
    SettingHint(stringResource(R.string.pdf_password_hint))
}
