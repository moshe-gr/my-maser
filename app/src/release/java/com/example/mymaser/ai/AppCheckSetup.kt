package com.example.mymaser.ai

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Only genuine installs of this app may spend the Gemini quota. */
internal fun installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
