package com.expensemanager.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.expensemanager.android.core.auth.AuthRepository
import com.expensemanager.android.core.auth.PasswordResetLink
import com.expensemanager.android.core.designsystem.financeColors
import kotlinx.coroutines.launch

private enum class AuthPage { LOGIN, REGISTER, ADD_ACCOUNT, FORGOT_PASSWORD, RESET_PASSWORD }

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    resetLink: PasswordResetLink?,
    isAddingAccount: Boolean,
) {
    val colors = financeColors()
    val coroutineScope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(AuthPage.LOGIN) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }
    var isWorking by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(resetLink) {
        if (resetLink != null) {
            page = AuthPage.RESET_PASSWORD
            errorMessage = null
            notice = null
        }
    }

    LaunchedEffect(isAddingAccount) {
        if (isAddingAccount) {
            page = AuthPage.ADD_ACCOUNT
            errorMessage = null
            notice = null
        } else if (page == AuthPage.ADD_ACCOUNT) {
            page = AuthPage.LOGIN
        }
    }

    val title = when (page) {
        AuthPage.LOGIN -> "Welcome back"
        AuthPage.REGISTER -> "Create your account"
        AuthPage.ADD_ACCOUNT -> "Add a profile"
        AuthPage.FORGOT_PASSWORD -> "Forgot password?"
        AuthPage.RESET_PASSWORD -> "Choose a new password"
    }
    val subtitle = when (page) {
        AuthPage.LOGIN -> "Log in to continue to RupeyaWise."
        AuthPage.REGISTER -> "Start managing your money with RupeyaWise."
        AuthPage.ADD_ACCOUNT -> "Choose a name for this profile. Email and password are optional."
        AuthPage.FORGOT_PASSWORD -> "We’ll email you a secure link to reset it."
        AuthPage.RESET_PASSWORD -> "Choose a strong password you haven’t used before."
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.appBackground).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = colors.primary, modifier = Modifier.height(42.dp))
        Text("RupeyaWise", color = colors.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Text(title, color = colors.primaryText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)

                if (page == AuthPage.LOGIN) {
                    AuthTextField(identifier, { identifier = it }, "Username or email", KeyboardType.Email)
                    PasswordField(password, { password = it }, "Password")
                }
                if (page == AuthPage.REGISTER) {
                    AuthTextField(username, { username = it }, "Username", KeyboardType.Text)
                    AuthTextField(email, { email = it }, "Email", KeyboardType.Email)
                    PasswordField(password, { password = it }, "Password")
                    PasswordField(passwordConfirmation, { passwordConfirmation = it }, "Confirm password")
                }
                if (page == AuthPage.ADD_ACCOUNT) {
                    AuthTextField(username, { username = it }, "Profile name", KeyboardType.Text)
                }
                if (page == AuthPage.FORGOT_PASSWORD) {
                    AuthTextField(email, { email = it }, "Email", KeyboardType.Email)
                }
                if (page == AuthPage.RESET_PASSWORD) {
                    PasswordField(password, { password = it }, "New password")
                    PasswordField(passwordConfirmation, { passwordConfirmation = it }, "Confirm new password")
                }

                errorMessage?.let { Text(it, color = colors.expense, style = MaterialTheme.typography.bodyMedium) }
                notice?.let { Text(it, color = colors.income, style = MaterialTheme.typography.bodyMedium) }

                Button(
                    onClick = {
                        errorMessage = null
                        notice = null
                        if (page == AuthPage.REGISTER || page == AuthPage.RESET_PASSWORD) {
                            if (password != passwordConfirmation) {
                                errorMessage = "The passwords do not match."
                                return@Button
                            }
                        }
                        if (page == AuthPage.LOGIN && (identifier.isBlank() || password.isBlank())) {
                            errorMessage = "Enter your username/email and password."
                            return@Button
                        }
                        if (page == AuthPage.REGISTER && (username.isBlank() || email.isBlank())) {
                            errorMessage = "Enter a username and email address."
                            return@Button
                        }
                        if (page == AuthPage.ADD_ACCOUNT && username.isBlank()) {
                            errorMessage = "Enter a profile name."
                            return@Button
                        }
                        if (page == AuthPage.FORGOT_PASSWORD && email.isBlank()) {
                            errorMessage = "Enter the email address for your account."
                            return@Button
                        }
                        val action = page
                        coroutineScope.launch {
                            isWorking = true
                            try {
                                when (action) {
                                    AuthPage.LOGIN -> authRepository.login(identifier, password)
                                    AuthPage.REGISTER -> authRepository.register(username, email, password, passwordConfirmation)
                                    AuthPage.ADD_ACCOUNT -> authRepository.createLocalAccount(username)
                                    AuthPage.FORGOT_PASSWORD -> {
                                        notice = authRepository.requestPasswordReset(email)
                                    }
                                    AuthPage.RESET_PASSWORD -> {
                                        val link = resetLink
                                        if (link == null) error("Open the reset link from your email again.")
                                        notice = authRepository.resetPassword(link, password, passwordConfirmation)
                                        authRepository.consumePasswordResetLink()
                                        password = ""
                                        passwordConfirmation = ""
                                        page = AuthPage.LOGIN
                                    }
                                }
                            } catch (failure: Exception) {
                                errorMessage = failure.message ?: "Something went wrong. Please try again."
                            } finally {
                                isWorking = false
                            }
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            when (page) {
                                AuthPage.LOGIN -> "Log in"
                                AuthPage.REGISTER -> "Create account"
                                AuthPage.ADD_ACCOUNT -> "Continue"
                                AuthPage.FORGOT_PASSWORD -> "Send reset link"
                                AuthPage.RESET_PASSWORD -> "Reset password"
                            },
                        )
                    }
                }

                when (page) {
                    AuthPage.LOGIN -> {
                        TextButton(onClick = { page = AuthPage.FORGOT_PASSWORD; errorMessage = null; notice = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Forgot password?")
                        }
                        TextButton(onClick = { page = AuthPage.REGISTER; errorMessage = null; notice = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("New to RupeyaWise? Create an account")
                        }
                    }
                    AuthPage.REGISTER -> {
                        TextButton(
                            onClick = { page = AuthPage.LOGIN; errorMessage = null },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Already have an account? Log in")
                        }
                        TextButton(
                            onClick = authRepository::continueAsGuest,
                            enabled = !isWorking,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Continue without signing up")
                        }
                    }
                    AuthPage.ADD_ACCOUNT -> Unit
                    AuthPage.FORGOT_PASSWORD, AuthPage.RESET_PASSWORD -> TextButton(
                        onClick = { page = AuthPage.LOGIN; errorMessage = null; notice = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("Back to login") }
                }

                if (isAddingAccount) {
                    TextButton(
                        onClick = authRepository::cancelAddAccount,
                        enabled = !isWorking,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Back to current profile")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(value: String, onValueChange: (String) -> Unit, label: String, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by rememberSaveable(label) { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
    )
}
