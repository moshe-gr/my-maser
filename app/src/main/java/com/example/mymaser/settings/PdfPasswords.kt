package com.example.mymaser.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import com.example.mymaser.dataStore
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "pdf_password"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_BYTES = 12
private const val TAG_BITS = 128

/** Saves the password for locked PDFs, encrypted with a key that never leaves the phone's keystore. */
suspend fun Context.savePdfPassword(password: String) {
    val sealed = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, pdfKey()) }
        cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8))
    }
    dataStore.edit { it[SettingsKeys.PDF_PASSWORD] = Base64.encodeToString(sealed, Base64.NO_WRAP) }
}

suspend fun Context.removePdfPassword() {
    dataStore.edit { it.remove(SettingsKeys.PDF_PASSWORD) }
}

/** The saved passwords; empty when none is saved or it can't be decrypted, e.g. after a restore. */
suspend fun Context.pdfPasswords(): List<String> {
    val stored = dataStore.data.first()[SettingsKeys.PDF_PASSWORD] ?: return emptyList()
    return withContext(Dispatchers.IO) {
        try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, pdfKey(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
            listOf(String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8))
        } catch (_: GeneralSecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}

private fun pdfKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
}
