package com.expensemanager.android

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensemanager.android.core.designsystem.FinanceTheme
import com.expensemanager.android.core.designsystem.FinanceThemeMode
import com.expensemanager.android.core.auth.PasswordResetLink
import com.expensemanager.android.feature.auth.AuthScreen
import com.expensemanager.android.core.presentation.FinanceViewModel
import com.expensemanager.android.core.presentation.FinanceViewModelFactory
import com.expensemanager.android.core.update.AppUpdateInfo
import com.expensemanager.android.feature.settings.CurrentReleaseDialog
import com.expensemanager.android.feature.settings.UpdateAvailableDialog
import com.expensemanager.android.ui.ExpenseManagerApp
import kotlinx.coroutines.launch

@androidx.compose.material3.ExperimentalMaterial3Api
class MainActivity : FragmentActivity() {
    private val applicationModel: ExpenseManagerApplication by lazy { application as ExpenseManagerApplication }
    private var onBalanceAuthenticated: (() -> Unit)? = null
    private lateinit var balanceBiometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        balanceBiometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onBalanceAuthenticated?.invoke()
                    onBalanceAuthenticated = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onBalanceAuthenticated = null
                }
            },
        )
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        handlePasswordResetLink(intent?.data)
        val application = applicationModel
        setContent {
            val themeMode by application.themePreferences.theme.collectAsStateWithLifecycle(FinanceThemeMode.MIDNIGHT)
            val session by application.authRepository.session.collectAsStateWithLifecycle()
            val resetLink by application.authRepository.resetLink.collectAsStateWithLifecycle()
            FinanceTheme(themeMode) {
                var showCurrentRelease by rememberSaveable {
                    mutableStateOf(application.appUpdateManager.shouldShowCurrentRelease())
                }
                var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = themeMode == FinanceThemeMode.ALMOND
                        isAppearanceLightNavigationBars = themeMode == FinanceThemeMode.ALMOND
                    }
                }
                LaunchedEffect(Unit) { application.authRepository.restoreSession() }
                LaunchedEffect(showCurrentRelease) {
                    if (!showCurrentRelease) {
                        availableUpdate = application.appUpdateManager.checkForUpdate()
                    }
                }
                when {
                    session == null -> AuthScreen(application.authRepository, resetLink)
                    else -> {
                        val authenticatedUser = session!!.user
                        val financeRepository = remember(authenticatedUser.id) {
                            application.financeRepository.apply {
                                selectOwner(authenticatedUser.id.toString())
                            }
                        }
                        LaunchedEffect(authenticatedUser.id) {
                            financeRepository.claimLocalData(authenticatedUser.id.toString())
                        }
                        val viewModel: FinanceViewModel = viewModel(
                            key = "finance-${authenticatedUser.id}",
                            factory = FinanceViewModelFactory(financeRepository),
                        )
                        ExpenseManagerApp(
                            viewModel = viewModel,
                            themeMode = themeMode,
                            currentUser = authenticatedUser,
                            onAuthenticateBalance = ::authenticateToRevealBalance,
                            onLogout = { lifecycleScope.launch { runCatching { application.authRepository.logout() } } },
                            onThemeChange = { selectedTheme ->
                                lifecycleScope.launch { application.themePreferences.setTheme(selectedTheme) }
                            },
                        )
                    }
                }

                if (showCurrentRelease) {
                    CurrentReleaseDialog(
                        onDismiss = {
                            application.appUpdateManager.markCurrentReleaseSeen()
                            showCurrentRelease = false
                        },
                    )
                } else {
                    availableUpdate?.let { update ->
                        UpdateAvailableDialog(
                            update = update,
                            onDownload = {
                                val opened = application.appUpdateManager.openDownload(update.downloadUrl)
                                if (!opened) {
                                    Toast.makeText(
                                        this,
                                        "No browser is available to download the update.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                availableUpdate = null
                            },
                            onDismiss = { availableUpdate = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePasswordResetLink(intent.data)
    }

    private fun handlePasswordResetLink(uri: Uri?) {
        if (uri?.scheme != "rupeyawise" || uri.host != "auth" || uri.path != "/reset") return
        val uid = uri.getQueryParameter("uid") ?: return
        val token = uri.getQueryParameter("token") ?: return
        applicationModel.authRepository.openPasswordReset(PasswordResetLink(uid, token))
    }

    private fun authenticateToRevealBalance(onAuthenticated: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        when (BiometricManager.from(this).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                onBalanceAuthenticated = onAuthenticated
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("View your balance")
                    .setSubtitle("Use your fingerprint or phone screen lock to reveal your amounts")
                    .setAllowedAuthenticators(authenticators)
                    .build()
                balanceBiometricPrompt.authenticate(promptInfo)
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                Toast.makeText(
                    this,
                    "Set up a fingerprint or phone screen lock in your phone settings first.",
                    Toast.LENGTH_LONG,
                ).show()

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                Toast.makeText(
                    this,
                    "Set up a phone screen lock to protect and reveal your amounts.",
                    Toast.LENGTH_LONG,
                ).show()

            else ->
                Toast.makeText(this, "Phone authentication is currently unavailable.", Toast.LENGTH_LONG).show()
        }
    }
}
