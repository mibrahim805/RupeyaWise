package com.expensemanager.android.core.data.preferences

import android.content.Context

class PrivacyPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun isAmountProtectionEnabled(userId: Long): Boolean =
        preferences.getBoolean(amountProtectionKey(userId), false)

    fun setAmountProtectionEnabled(userId: Long, enabled: Boolean) {
        preferences.edit().putBoolean(amountProtectionKey(userId), enabled).apply()
    }

    fun removeUser(userId: Long) {
        preferences.edit().remove(amountProtectionKey(userId)).apply()
    }

    private fun amountProtectionKey(userId: Long) = "amount_protection_enabled_$userId"

    private companion object {
        const val PreferencesName = "privacy_preferences"
    }
}
