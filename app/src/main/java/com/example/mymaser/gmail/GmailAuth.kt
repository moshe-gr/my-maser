package com.example.mymaser.gmail

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Read-only: the app can search and read mail, never send, change or delete it. */
private val GMAIL_READONLY = Scope("https://www.googleapis.com/auth/gmail.readonly")

fun gmailAuthorizationRequest(): AuthorizationRequest =
    AuthorizationRequest.builder().setRequestedScopes(listOf(GMAIL_READONLY)).build()

/**
 * An access token without showing anything, or null when the user has to approve again.
 * Throws [IOException] when Google couldn't be reached, so the caller can try later.
 */
suspend fun silentGmailToken(context: Context): String? = suspendCancellableCoroutine { continuation ->
    Identity.getAuthorizationClient(context).authorize(gmailAuthorizationRequest())
        .addOnSuccessListener { result ->
            continuation.resume(if (result.hasResolution()) null else result.accessToken)
        }
        .addOnFailureListener { error ->
            val transient = error !is ApiException || error.statusCode in TRANSIENT_STATUS_CODES
            if (transient) continuation.resumeWithException(IOException(error)) else continuation.resume(null)
        }
}

/** Failures worth retrying later; anything else (e.g. access revoked) needs the user to connect again. */
private val TRANSIENT_STATUS_CODES = setOf(
    CommonStatusCodes.NETWORK_ERROR,
    CommonStatusCodes.TIMEOUT,
    CommonStatusCodes.INTERNAL_ERROR,
    CommonStatusCodes.API_NOT_CONNECTED,
    CommonStatusCodes.INTERRUPTED
)

/** Drops a rejected token from Google's cache so the next request gets a fresh one. */
suspend fun clearGmailToken(context: Context, token: String) {
    suspendCancellableCoroutine { continuation ->
        Identity.getAuthorizationClient(context)
            .clearToken(ClearTokenRequest.builder().setToken(token).build())
            .addOnCompleteListener { continuation.resume(Unit) }
    }
}
