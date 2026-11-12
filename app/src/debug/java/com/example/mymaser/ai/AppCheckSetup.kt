package com.example.mymaser.ai

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug builds log a token to Logcat; register it in the Firebase console's App Check page. */
internal fun installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
