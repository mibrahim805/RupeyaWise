package com.expensemanager.android.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedAuthSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun read(): AuthSession? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val cipherText = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8)).let { json ->
                AuthSession(
                    token = json.getString("token"),
                    user = AuthUser(
                        id = json.getLong("id"),
                        username = json.getString("username"),
                        email = json.optString("email"),
                    ),
                    isGuest = json.optBoolean("is_guest", false),
                )
            }
        }.getOrElse {
            clear()
            null
        }
    }

    fun save(session: AuthSession) {
        val json = JSONObject()
            .put("token", session.token)
            .put("id", session.user.id)
            .put("username", session.user.username)
            .put("email", session.user.email)
            .put("is_guest", session.isGuest)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val packed = cipher.iv + cipher.doFinal(json)
        preferences.edit().putString(SESSION_KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun clear() {
        preferences.edit().remove(SESSION_KEY).apply()
    }

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
}
