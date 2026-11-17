package com.sharedshoppinglists.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.sharedshoppinglists.app.BuildConfig
import com.sharedshoppinglists.app.domain.model.PendingInvitation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compartir listas a traves del backend (/share/...). El backend usa permisos de administrador
 * porque el invitado todavia no es miembro de la lista y no podria agregarse solo.
 * Se identifica al usuario con su ID token de Firebase.
 */
@Singleton
class ShareApiClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val baseUrl = BuildConfig.API_BASE_URL
    private val json = "application/json; charset=utf-8".toMediaType()

    data class Accepted(val listId: String, val listName: String, val alreadyMember: Boolean)

    suspend fun inviteByEmail(listId: String, email: String): String =
        call("POST", "/share/lists/$listId/invite", JSONObject().put("email", email))
            .let { JSONObject(it).optString("message", "Invitación enviada") }

    suspend fun createLink(listId: String): String =
        JSONObject(call("POST", "/share/lists/$listId/link")).getString("url")

    suspend fun pendingInvitations(): List<PendingInvitation> {
        val arr = JSONArray(call("GET", "/share/invitations"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PendingInvitation(
                id = o.getString("id"),
                listId = o.optString("listId"),
                listName = o.optString("listName", "Lista"),
                inviteeEmail = o.optString("inviteeEmail")
            )
        }
    }

    suspend fun accept(invitationId: String): Accepted {
        val o = JSONObject(call("POST", "/share/invitations/$invitationId/accept"))
        return Accepted(o.getString("listId"), o.optString("listName", "la lista"), o.optBoolean("alreadyMember"))
    }

    suspend fun decline(invitationId: String) {
        call("POST", "/share/invitations/$invitationId/decline")
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Iniciá sesión para compartir listas.")
        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException("No se pudo verificar tu sesión.")
        return withContext(Dispatchers.IO) {
            val reqBody = when {
                body != null -> body.toString().toRequestBody(json)
                method == "POST" -> "{}".toRequestBody(json)
                else -> null
            }
            val req = Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer $token")
                .method(method, reqBody)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    // FastAPI devuelve {"detail": "mensaje"}
                    val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                    throw IllegalStateException(
                        detail?.takeIf { it.isNotBlank() } ?: "No se pudo conectar con el servidor (${resp.code})."
                    )
                }
                text
            }
        }
    }
}
