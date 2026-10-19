package com.expensemanager.android.core.data

import com.expensemanager.android.core.data.local.BudgetEntity
import com.expensemanager.android.core.data.local.TransactionEntity
import com.expensemanager.android.core.model.FinanceCategory
import com.expensemanager.android.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class FinanceBackupCodecTest {
    private val backup = FinanceBackupData(
        transactions = listOf(
            TransactionEntity(
                type = TransactionType.EXPENSE.name,
                amountMinor = 2_450,
                currencyCode = "PKR",
                categoryKey = FinanceCategory.FOOD.key,
                description = "Lunch",
                accountId = "cash",
                accountName = "Cash",
                timestampMillis = 1_700_000_000_000,
                requestId = "backup-test-request",
                ownerId = "owner-1",
            ),
        ),
        budgets = listOf(
            BudgetEntity(
                ownerId = "owner-1",
                categoryKey = FinanceCategory.FOOD.key,
                limitMinor = 30_000,
            ),
        ),
    )

    @Test
    fun encryptedBackupRoundTripsWithoutExposingFinancialData() {
        val encoded = FinanceBackupCodec.encode(backup, "strong-password")

        assertFalse(String(encoded, StandardCharsets.UTF_8).contains("Lunch"))

        val decoded = FinanceBackupCodec.decode(encoded, "strong-password")
        assertEquals(backup.transactions.single().copy(ownerId = "local"), decoded.transactions.single())
        assertEquals(backup.budgets.single().copy(ownerId = "local"), decoded.budgets.single())
    }

    @Test
    fun wrongPasswordCannotDecryptBackup() {
        val encoded = FinanceBackupCodec.encode(backup, "strong-password")

        assertThrows(IllegalArgumentException::class.java) {
            FinanceBackupCodec.decode(encoded, "wrong-password")
        }
    }
}
