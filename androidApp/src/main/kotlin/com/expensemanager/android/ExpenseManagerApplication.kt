package com.expensemanager.android

import android.app.Application
import com.expensemanager.android.core.auth.AuthRepository
import com.expensemanager.android.core.auth.EncryptedAuthSessionStore
import com.expensemanager.android.core.data.FinanceRepository
import com.expensemanager.android.core.data.local.FinanceDatabase
import com.expensemanager.android.core.data.preferences.ThemePreferences
import com.expensemanager.android.core.data.preferences.PrivacyPreferences
import com.expensemanager.android.core.update.AppUpdateManager

class ExpenseManagerApplication : Application() {
    private val database by lazy { FinanceDatabase.getInstance(this) }
    val financeRepository by lazy { FinanceRepository(database.transactionDao(), database.budgetDao()) }
    val themePreferences by lazy { ThemePreferences(this) }
    val privacyPreferences by lazy { PrivacyPreferences(this) }
    val authRepository by lazy { AuthRepository(EncryptedAuthSessionStore(this)) }
    val appUpdateManager by lazy { AppUpdateManager(this) }
}
