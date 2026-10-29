package com.expensemanager.android.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedAuthSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun read(): AuthSession? {
        val state = readState()
        return state.sessions.firstOrNull { it.user.id == state.activeUserId }
    }

    fun readAll(): List<AuthSession> = readState().sessions

    fun save(session: AuthSession) {
        val state = readState()
        val sessions = listOf(session) + state.sessions.filterNot { it.user.id == session.user.id }
        writeState(SessionState(activeUserId = session.user.id, sessions = sessions))
    }

    fun activate(userId: Long): AuthSession? {
        val state = readState()
        val session = state.sessions.firstOrNull { it.user.id == userId } ?: return null
        writeState(state.copy(activeUserId = userId))
        return session
    }

    fun remove(userId: Long): AuthSession? {
        val state = readState()
        if (state.sessions.none { it.user.id == userId }) {
            return state.sessions.firstOrNull { it.user.id == state.activeUserId }
        }
        val remaining = state.sessions.filterNot { it.user.id == userId }
        val next = remaining.firstOrNull()
        if (remaining.isEmpty()) {
            clear()
        } else {
            writeState(SessionState(activeUserId = next?.user?.id, sessions = remaining))
        }
        return next
    }

    fun clear() {
        preferences.edit().remove(SESSION_KEY).apply()
    }

    private fun readState(): SessionState {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return SessionState()
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val cipherText = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8)).toSessionState()
        }.getOrElse {
            clear()
            SessionState()
        }
    }

    private fun writeState(state: SessionState) {
        val json = JSONObject()
            .put("active_user_id", state.activeUserId)
            .put(
                "sessions",
                JSONArray().apply { state.sessions.forEach { session -> put(session.toJson()) } },
            )
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val packed = cipher.iv + cipher.doFinal(json)
        preferences.edit().putString(SESSION_KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    private fun JSONObject.toSessionState(): SessionState {
        val sessionsJson = optJSONArray("sessions")
        if (sessionsJson == null) {
            val legacySession = toSession()
            return SessionState(activeUserId = legacySession.user.id, sessions = listOf(legacySession))
        }
        val sessions = (0 until sessionsJson.length()).mapNotNull { index ->
            sessionsJson.optJSONObject(index)?.let { json -> runCatching { json.toSession() }.getOrNull() }
        }
        val activeUserId = if (has("active_user_id") && !isNull("active_user_id")) {
            optLong("active_user_id")
        } else {
            null
        }
        return SessionState(activeUserId = activeUserId, sessions = sessions)
    }

    private fun JSONObject.toSession() = AuthSession(
        token = getString("token"),
        user = AuthUser(
            id = getLong("id"),
            username = getString("username"),
            email = optString("email"),
        ),
        isGuest = optBoolean("is_guest", false),
        isLocal = optBoolean("is_local", false),
    )

    private fun AuthSession.toJson() = JSONObject()
        .put("token", token)
        .put("id", user.id)
        .put("username", user.username)
        .put("email", user.email)
        .put("is_guest", isGuest)
        .put("is_local", isLocal)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val SESSION_KEY = "encrypted_session"
        const val KEY_ALIAS = "rupeyawise_auth_session"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }

    private data class SessionState(
        val activeUserId: Long? = null,
        val sessions: List<AuthSession> = emptyList(),
    )
}
