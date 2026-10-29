package com.expensemanager.android.core.auth

import com.expensemanager.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AuthRepository(private val sessionStore: EncryptedAuthSessionStore) {
    private val _session = MutableStateFlow(sessionStore.read())
    val session = _session.asStateFlow()

    private val _profiles = MutableStateFlow(sessionStore.readAll().toProfiles())
    val profiles = _profiles.asStateFlow()

    private val _isAddingAccount = MutableStateFlow(false)
    val isAddingAccount = _isAddingAccount.asStateFlow()

    private val _resetLink = MutableStateFlow<PasswordResetLink?>(null)
    val resetLink = _resetLink.asStateFlow()

    suspend fun restoreSession() {
        val current = _session.value ?: return
        if (current.isGuest) return
        runCatching { request("me/", token = current.token) }
            .onSuccess { response ->
                val user = response.getJSONObject("user").toAuthUser()
                updateSession(current.copy(user = user))
            }
            .onFailure { error ->
                if ((error as? AuthApiException)?.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    removeCurrentSession()
                }
            }
    }

    suspend fun login(identifier: String, password: String) {
        val response = request(
            "login/",
            body = JSONObject().put("username_or_email", identifier.trim()).put("password", password),
        )
        saveResponseSession(response)
    }

    suspend fun register(username: String, email: String, password: String, confirmation: String) {
        val response = request(
            "register/",
            body = JSONObject()
                .put("username", username.trim())
                .put("email", email.trim())
                .put("password", password)
                .put("password_confirmation", confirmation),
        )
        saveResponseSession(response)
    }

    fun continueAsGuest() {
        updateSession(
            AuthSession(
                token = "",
                user = AuthUser(
                    id = GUEST_USER_ID,
                    username = "Guest",
                    email = "",
                ),
                isGuest = true,
            ),
        )
    }

    fun beginAddAccount() {
        _isAddingAccount.value = true
        _session.value = null
    }

    fun cancelAddAccount() {
        _isAddingAccount.value = false
        _session.value = sessionStore.read()
    }

    fun switchAccount(userId: Long) {
        val selected = sessionStore.activate(userId) ?: return
        _isAddingAccount.value = false
        _session.value = selected
    }

    suspend fun requestPasswordReset(email: String): String =
        request("password-reset/", JSONObject().put("email", email.trim())).getString("message")

    suspend fun resetPassword(link: PasswordResetLink, password: String, confirmation: String): String =
        request(
            "password-reset/confirm/",
            JSONObject()
                .put("uid", link.uid)
                .put("token", link.token)
                .put("password", password)
                .put("password_confirmation", confirmation),
        ).getString("message")

    suspend fun logout() {
        val current = _session.value
        try {
            if (current != null && !current.isGuest) {
                request("logout/", body = JSONObject(), token = current.token)
            }
        } finally {
            current?.let { removeSession(it.user.id) }
        }
    }

    fun openPasswordReset(link: PasswordResetLink) {
        _isAddingAccount.value = false
        _session.value = null
        _resetLink.value = link
    }

    fun consumePasswordResetLink() {
        _resetLink.value = null
    }

    private suspend fun saveResponseSession(response: JSONObject) {
        val session = AuthSession(
            token = response.getString("token"),
            user = response.getJSONObject("user").toAuthUser(),
        )
        sessionStore.remove(GUEST_USER_ID)
        updateSession(session)
    }

    private fun updateSession(session: AuthSession) {
        sessionStore.save(session)
        _profiles.value = sessionStore.readAll().toProfiles()
        _isAddingAccount.value = false
        _session.value = session
    }

    private fun removeCurrentSession() {
        _session.value?.let { removeSession(it.user.id) }
    }

    private fun removeSession(userId: Long) {
        _session.value = sessionStore.remove(userId)
        _profiles.value = sessionStore.readAll().toProfiles()
    }

    private suspend fun request(
        path: String,
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.AUTH_API_BASE_URL.trim()
        if (baseUrl.isBlank() || baseUrl.contains("your-api")) {
            throw AuthApiException("Set the Django server URL in rupeyawiseApiBaseUrl to connect your account.")
        }
        val connection = (URL(baseUrl.trimEnd('/') + "/api/auth/" + path.trimStart('/')).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(responseBody) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val details = json.optJSONObject("fields")?.let { fields ->
                    fields.keys().asSequence().mapNotNull { key ->
                        fields.optJSONArray(key)?.optString(0)?.takeIf(String::isNotBlank)
                    }.firstOrNull()
                }
                throw AuthApiException(details ?: json.optString("error", "Request failed. Please try again."), status)
            }
            json
        } catch (error: AuthApiException) {
            throw error
        } catch (_: Exception) {
            throw AuthApiException("Could not connect to the server. Check your internet connection and try again.")
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toAuthUser() = AuthUser(
        id = getLong("id"),
        username = getString("username"),
        email = optString("email"),
    )

    private fun List<AuthSession>.toProfiles() = map { session ->
        AuthProfile(user = session.user, isGuest = session.isGuest)
    }

    private companion object {
        const val GUEST_USER_ID = 0L
    }
}

class AuthApiException(message: String, val statusCode: Int? = null) : Exception(message)
