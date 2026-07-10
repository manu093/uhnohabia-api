package com.sharedshoppinglists.app.presentation.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleSignInHelper {

    const val WEB_CLIENT_ID =
        "654911456795-gplmcg0qoogcfo7vuop60vcc76j8t6mp.apps.googleusercontent.com"

    suspend fun getGoogleIdToken(activity: Activity): String {
        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activity, request)
        val googleIdTokenCredential =
            GoogleIdTokenCredential.createFrom(result.credential.data)

        return googleIdTokenCredential.idToken
    }
}
