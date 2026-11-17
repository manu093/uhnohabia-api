package com.sharedshoppinglists.app.presentation.shared

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Invitacion recibida por link (https://.../invite/{id} o uhnohabia://invite/{id}).
 * MainActivity la deja aca y ShoppingListViewModel la acepta cuando hay sesion iniciada.
 */
object PendingInviteLink {
    val id = MutableStateFlow<String?>(null)

    /** Extrae el id de invitacion de un link, o null si no es un link de invitacion. */
    fun parse(uri: android.net.Uri?): String? =
        uri?.let { parse(it.scheme, it.host, it.pathSegments ?: emptyList()) }

    fun parse(scheme: String?, host: String?, segments: List<String>): String? = when {
        scheme == "uhnohabia" && host == "invite" -> segments.firstOrNull()
        (scheme == "https" || scheme == "http") && segments.size >= 2 && segments[0] == "invite" -> segments[1]
        else -> null
    }?.takeIf { it.isNotBlank() && it.length <= 64 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
}
