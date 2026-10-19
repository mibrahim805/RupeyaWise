package com.expensemanager.android.feature.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.expensemanager.android.core.designsystem.FinanceThemeMode
import com.expensemanager.android.core.designsystem.financeColors
import com.expensemanager.android.core.designsystem.financeThemeColors
import com.expensemanager.android.core.auth.AuthUser
import com.expensemanager.android.core.data.BackupRestoreResult
import com.expensemanager.android.core.update.CurrentRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    selectedTheme: FinanceThemeMode,
    currentUser: AuthUser,
    amountProtectionEnabled: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onThemeSelected: (FinanceThemeMode) -> Unit,
    onAmountProtectionChange: (Boolean) -> Unit,
    onCreateBackup: suspend (String) -> ByteArray,
    onRestoreBackup: suspend (ByteArray, String) -> BackupRestoreResult,
) {
    val colors = financeColors()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var passwordMode by remember { mutableStateOf<BackupPasswordMode?>(null) }
    var pendingRestore by remember { mutableStateOf<ByteArray?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var isBackupWorking by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isBackupWorking = true
            backupStatus = null
            runCatching { readBackupFile(context, uri) }
                .onSuccess { bytes ->
                    pendingRestore = bytes
                    passwordMode = BackupPasswordMode.RESTORE
                }
                .onFailure { backupStatus = it.message ?: "Could not read this backup file." }
            isBackupWorking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to dashboard" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        Text("Appearance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Choose how RupeyaWise looks on this device.",
            color = colors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        ThemeOption(
            title = "Midnight Blue",
            description = "Premium dark finance theme",
            mode = FinanceThemeMode.MIDNIGHT,
            selected = selectedTheme == FinanceThemeMode.MIDNIGHT,
            preview = financeThemeColors(FinanceThemeMode.MIDNIGHT).appBackground,
            iconTint = financeThemeColors(FinanceThemeMode.MIDNIGHT).primary,
            onClick = { onThemeSelected(FinanceThemeMode.MIDNIGHT) },
        )
        Spacer(Modifier.height(10.dp))
        ThemeOption(
            title = "Almond Cream",
            description = "Warm, elegant light theme",
            mode = FinanceThemeMode.ALMOND,
            selected = selectedTheme == FinanceThemeMode.ALMOND,
            preview = financeThemeColors(FinanceThemeMode.ALMOND).appBackground,
            iconTint = financeThemeColors(FinanceThemeMode.ALMOND).primary,
            onClick = { onThemeSelected(FinanceThemeMode.ALMOND) },
        )

        Text("Privacy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 22.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onAmountProtectionChange(!amountProtectionEnabled) },
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Protect financial amounts", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (amountProtectionEnabled) {
                            "Fingerprint, PIN, pattern, or password is required to reveal amounts."
                        } else {
                            "Off by default. Your amounts remain visible without authentication."
                        },
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = amountProtectionEnabled,
                    onCheckedChange = onAmountProtectionChange,
                )
            }
        }

        Text("Account", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 22.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(currentUser.username, fontWeight = FontWeight.SemiBold)
                Text(currentUser.email, color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Log out")
        }

        Text("Data & backup", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 22.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Encrypted Gmail backup", fontWeight = FontWeight.SemiBold)
                Text(
                    "Create a backup only when you want one. RupeyaWise encrypts your transactions and budgets before you send the file to your Gmail account.",
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { passwordMode = BackupPasswordMode.CREATE },
                    enabled = !isBackupWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Back up with Gmail")
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/json")) },
                    enabled = !isBackupWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Restore encrypted backup")
                }
                if (isBackupWorking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Preparing your data…", color = colors.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
                backupStatus?.let { status ->
                    Text(status, color = colors.secondaryText, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Keep the password safe. It cannot be recovered by RupeyaWise.",
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Text("About", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 22.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenWhatsNew),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = colors.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("What's new", fontWeight = FontWeight.SemiBold)
                    Text(
                        "RupeyaWise ${CurrentRelease.versionName}",
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.secondaryText)
            }
        }
    }

    passwordMode?.let { mode ->
        BackupPasswordDialog(
            mode = mode,
            onDismiss = {
                passwordMode = null
                if (mode == BackupPasswordMode.RESTORE) pendingRestore = null
            },
            onConfirm = { password ->
                passwordMode = null
                coroutineScope.launch {
                    isBackupWorking = true
                    backupStatus = null
                    when (mode) {
                        BackupPasswordMode.CREATE -> runCatching { onCreateBackup(password) }
                            .onSuccess { bytes ->
                                runCatching {
                                    val shareIntent = shareBackup(context, bytes, currentUser.email)
                                    context.startActivity(Intent.createChooser(shareIntent, "Send backup with Gmail"))
                                }
                                    .onSuccess {
                                        backupStatus = "Encrypted backup created. Send the attached file to keep it safe."
                                    }
                                    .onFailure { backupStatus = it.message ?: "Could not share the backup." }
                            }
                            .onFailure { backupStatus = it.message ?: "Could not create the backup." }

                        BackupPasswordMode.RESTORE -> {
                            val bytes = pendingRestore
                            pendingRestore = null
                            if (bytes == null) {
                                backupStatus = "Choose a backup file first."
                            } else {
                                runCatching { onRestoreBackup(bytes, password) }
                                    .onSuccess { result ->
                                        backupStatus = "Restored ${result.transactionsImported} transactions and ${result.budgetsRestored} budgets."
                                    }
                                    .onFailure { backupStatus = it.message ?: "Could not restore this backup." }
                            }
                        }
                    }
                    isBackupWorking = false
                }
            },
        )
    }
}

private enum class BackupPasswordMode { CREATE, RESTORE }

@Composable
private fun BackupPasswordDialog(
    mode: BackupPasswordMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember(mode) { mutableStateOf("") }
    var confirmation by remember(mode) { mutableStateOf("") }
    val isCreate = mode == BackupPasswordMode.CREATE
    val isValid = password.length >= 6 && (!isCreate || password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreate) "Protect your backup" else "Unlock your backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isCreate) {
                        "Enter a password used only for this backup. You will need it when restoring."
                    } else {
                        "Transactions are merged without duplicates, and matching budgets are updated. Enter the password used to create this backup."
                    },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Backup password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (isCreate) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = {
                            if (confirmation.isNotEmpty() && password != confirmation) Text("Passwords do not match")
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = isValid) {
                Text(if (isCreate) "Create backup" else "Restore")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private suspend fun readBackupFile(context: Context, uri: android.net.Uri): ByteArray = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= MaxBackupFileSizeBytes) { "This backup file is too large." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
        ?: throw IllegalArgumentException("Could not open this backup file.")
    bytes
}

private suspend fun shareBackup(context: Context, bytes: ByteArray, email: String): Intent = withContext(Dispatchers.IO) {
    val backupDirectory = File(context.cacheDir, "backups").apply { mkdirs() }
    val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val backupFile = File(backupDirectory, "RupeyaWise-backup-$date.rwb").apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
    Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, "RupeyaWise encrypted backup – $date")
        putExtra(Intent.EXTRA_TEXT, "This encrypted RupeyaWise backup can be restored from Settings > Data & backup.")
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("RupeyaWise encrypted backup", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private const val MaxBackupFileSizeBytes = 20 * 1024 * 1024

@Composable
private fun ThemeOption(
    title: String,
    description: String,
    mode: FinanceThemeMode,
    selected: Boolean,
    preview: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val colors = financeColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title theme${if (selected) ", selected" else ""}" },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.primary else colors.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (mode == FinanceThemeMode.MIDNIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, color = colors.secondaryText, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(preview),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = iconTint, modifier = Modifier.size(18.dp))
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
