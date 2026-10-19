package com.expensemanager.android.core.data

import com.expensemanager.android.core.data.local.BudgetEntity
import com.expensemanager.android.core.data.local.FinanceDatabase
import com.expensemanager.android.core.data.local.TransactionEntity
import com.expensemanager.android.core.model.TransactionType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupRestoreResult(
    val transactionsImported: Int,
    val budgetsRestored: Int,
)

internal data class FinanceBackupData(
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
)

internal object FinanceBackupCodec {
    private const val FormatVersion = 1
    private const val KeySizeBits = 256
    private const val SaltSizeBytes = 16
    private const val IvSizeBytes = 12
    private const val Pbkdf2Iterations = 150_000
    private const val MaxRecords = 100_000
    private const val MaxStringBytes = 1_000_000
    private const val MaxEncryptedPayloadBytes = 20 * 1024 * 1024
    private val magic = byteArrayOf('R'.code.toByte(), 'W'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private val secureRandom = SecureRandom()
    private val additionalData = "RupeyaWiseBackup:$FormatVersion".toByteArray(StandardCharsets.UTF_8)
    private val supportedTransactionTypes = TransactionType.entries.mapTo(hashSetOf()) { it.name }

    fun encode(backup: FinanceBackupData, password: String): ByteArray {
        requireValidPassword(password)
        require(backup.transactions.size <= MaxRecords && backup.budgets.size <= MaxRecords) {
            "There are too many records to back up."
        }
        val salt = ByteArray(SaltSizeBytes).also(secureRandom::nextBytes)
        val iv = ByteArray(IvSizeBytes).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(additionalData)
        val encrypted = cipher.doFinal(backup.toPayload())
        require(encrypted.size <= MaxEncryptedPayloadBytes) { "The backup is too large." }

        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(magic)
                output.writeInt(FormatVersion)
                output.writeSizedBytes(salt)
                output.writeSizedBytes(iv)
                output.writeSizedBytes(encrypted)
            }
            buffer.toByteArray()
        }
    }

    fun decode(bytes: ByteArray, password: String): FinanceBackupData {
        requireValidPassword(password)
        require(bytes.size <= MaxEncryptedPayloadBytes + 128) { "This backup file is too large." }
        try {
            val decryptedPayload = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val fileMagic = ByteArray(magic.size).also(input::readFully)
                require(fileMagic.contentEquals(magic)) { "This is not a valid RupeyaWise backup." }
                require(input.readInt() == FormatVersion) { "This backup format is not supported." }
                val salt = input.readSizedBytes(SaltSizeBytes)
                val iv = input.readSizedBytes(IvSizeBytes)
                val encrypted = input.readSizedBytes(MaxEncryptedPayloadBytes)
                require(input.read() == -1) { "This backup contains unexpected data." }

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
                cipher.updateAAD(additionalData)
                cipher.doFinal(encrypted)
            }
            return decryptedPayload.toBackupData()
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect password or damaged backup.")
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("This backup is damaged or cannot be read.")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, Pbkdf2Iterations, KeySizeBits)
        return try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun requireValidPassword(password: String) {
        require(password.length >= 6) { "Use at least 6 characters for your backup password." }
    }

    private fun FinanceBackupData.toPayload(): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeLong(System.currentTimeMillis())
            output.writeInt(transactions.size)
            transactions.forEach { transaction -> output.writeTransaction(transaction) }
            output.writeInt(budgets.size)
            budgets.forEach { budget -> output.writeBudget(budget) }
        }
        buffer.toByteArray()
    }

    private fun ByteArray.toBackupData(): FinanceBackupData =
        DataInputStream(ByteArrayInputStream(this)).use { input ->
            input.readLong() // Creation time is metadata reserved for future restore history.
            val transactionCount = input.readRecordCount()
            val transactions = List(transactionCount) { input.readTransaction() }
            val budgetCount = input.readRecordCount()
            val budgets = List(budgetCount) { input.readBudget() }
            require(input.read() == -1) { "Backup contains unexpected data." }
            FinanceBackupData(transactions, budgets)
        }

    private fun DataOutputStream.writeTransaction(transaction: TransactionEntity) {
        writeString(transaction.type)
        writeLong(transaction.amountMinor)
        writeString(transaction.currencyCode)
        writeString(transaction.categoryKey)
        writeString(transaction.description)
        writeString(transaction.accountId)
        writeString(transaction.accountName)
        writeNullableString(transaction.destinationAccountId)
        writeNullableString(transaction.destinationAccountName)
        writeLong(transaction.timestampMillis)
        writeNullableString(transaction.requestId)
    }

    private fun DataInputStream.readTransaction(): TransactionEntity {
        val type = readString()
        val amountMinor = readLong()
        val currencyCode = readString()
        val categoryKey = readString()
        val description = readString()
        val accountId = readString()
        val accountName = readString()
        val destinationAccountId = readNullableString()
        val destinationAccountName = readNullableString()
        val timestampMillis = readLong()
        val requestId = readNullableString()
        require(type in supportedTransactionTypes) { "Backup contains an invalid transaction type." }
        require(amountMinor > 0 && timestampMillis > 0 && currencyCode.isNotBlank()) {
            "Backup contains an invalid transaction."
        }
        return TransactionEntity(
            type = type,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            categoryKey = categoryKey,
            description = description,
            accountId = accountId,
            accountName = accountName,
            destinationAccountId = destinationAccountId,
            destinationAccountName = destinationAccountName,
            timestampMillis = timestampMillis,
            requestId = requestId,
            ownerId = FinanceDatabase.LOCAL_OWNER_ID,
        )
    }

    private fun DataOutputStream.writeBudget(budget: BudgetEntity) {
        writeString(budget.categoryKey)
        writeLong(budget.limitMinor)
        writeString(budget.currencyCode)
    }

    private fun DataInputStream.readBudget(): BudgetEntity {
        val categoryKey = readString()
        val limitMinor = readLong()
        val currencyCode = readString()
        require(limitMinor > 0 && categoryKey.isNotBlank() && currencyCode.isNotBlank()) {
            "Backup contains an invalid budget."
        }
        return BudgetEntity(
            categoryKey = categoryKey,
            limitMinor = limitMinor,
            currencyCode = currencyCode,
        )
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MaxStringBytes) { "A backup value is too large." }
        writeSizedBytes(bytes)
    }

    private fun DataInputStream.readString(): String =
        String(readSizedBytes(MaxStringBytes), StandardCharsets.UTF_8)

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readString() else null

    private fun DataOutputStream.writeSizedBytes(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedBytes(maximumSize: Int): ByteArray {
        val size = readInt()
        require(size in 0..maximumSize) { "Backup contains an invalid data block." }
        return ByteArray(size).also(::readFully)
    }

    private fun DataInputStream.readRecordCount(): Int {
        val count = readInt()
        require(count in 0..MaxRecords) { "Backup contains too many records." }
        return count
    }
}
