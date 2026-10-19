package com.expensemanager.android.core.data

import com.expensemanager.android.core.data.local.BudgetDao
import com.expensemanager.android.core.data.local.BudgetEntity
import com.expensemanager.android.core.data.local.TransactionDao
import com.expensemanager.android.core.data.local.TransactionEntity
import com.expensemanager.android.core.model.Account
import com.expensemanager.android.core.model.CategoryBudget
import com.expensemanager.android.core.model.FinanceCategory
import com.expensemanager.android.core.model.FinanceTransaction
import com.expensemanager.android.core.model.TransactionDraft
import com.expensemanager.android.core.model.TransactionType
import com.expensemanager.android.core.model.validateTransactionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FinanceRepository(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
) {
    private val ownerId = MutableStateFlow(com.expensemanager.android.core.data.local.FinanceDatabase.LOCAL_OWNER_ID)

    val transactions: Flow<List<FinanceTransaction>> = ownerId
        .flatMapLatest { currentOwner -> transactionDao.observeAll(currentOwner) }
        .map { records ->
            records.map { record ->
                FinanceTransaction(
                    id = record.id,
                    type = TransactionType.valueOf(record.type),
                    amountMinor = record.amountMinor,
                    currencyCode = record.currencyCode,
                    category = FinanceCategory.fromKey(record.categoryKey),
                    description = record.description,
                    account = Account(record.accountId, record.accountName, record.currencyCode),
                    destinationAccount = record.destinationAccountId?.let {
                        Account(it, record.destinationAccountName.orEmpty(), record.currencyCode)
                    },
                    timestampMillis = record.timestampMillis,
                )
            }
        }

    val budgets: Flow<List<CategoryBudget>> = ownerId
        .flatMapLatest { currentOwner -> budgetDao.observeAll(currentOwner) }
        .map { records ->
            records.map { record ->
                CategoryBudget(
                    category = FinanceCategory.fromKey(record.categoryKey),
                    limitMinor = record.limitMinor,
                    currencyCode = record.currencyCode,
                )
            }
        }

    fun selectOwner(userId: String) {
        require(userId.isNotBlank())
        ownerId.value = userId
    }

    suspend fun claimLocalData(userId: String) {
        require(userId.isNotBlank())
        transactionDao.claimLocalTransactions(userId, com.expensemanager.android.core.data.local.FinanceDatabase.LOCAL_OWNER_ID)
        budgetDao.claimLocalBudgets(userId, com.expensemanager.android.core.data.local.FinanceDatabase.LOCAL_OWNER_ID)
    }

    suspend fun createEncryptedBackup(password: String): ByteArray {
        val currentOwner = ownerId.value
        val backup = FinanceBackupData(
            transactions = transactionDao.getAll(currentOwner),
            budgets = budgetDao.getAll(currentOwner),
        )
        return withContext(Dispatchers.Default) {
            FinanceBackupCodec.encode(backup, password)
        }
    }

    suspend fun restoreEncryptedBackup(bytes: ByteArray, password: String): BackupRestoreResult {
        val backup = withContext(Dispatchers.Default) {
            FinanceBackupCodec.decode(bytes, password)
        }
        val currentOwner = ownerId.value
        val existingTransactions = transactionDao.getAll(currentOwner)
            .mapTo(mutableSetOf()) { it.withoutDatabaseIdentity() }
        val transactionsToImport = backup.transactions.mapNotNull { transaction ->
            val ownedTransaction = transaction.copy(id = 0, ownerId = currentOwner)
            ownedTransaction.takeIf { existingTransactions.add(it.withoutDatabaseIdentity()) }
        }
        val insertedTransactions = transactionDao.insertAll(transactionsToImport).count { it != -1L }
        val ownedBudgets = backup.budgets.map { it.copy(id = 0, ownerId = currentOwner) }
        budgetDao.upsertAll(ownedBudgets)
        return BackupRestoreResult(
            transactionsImported = insertedTransactions,
            budgetsRestored = ownedBudgets.size,
        )
    }

    suspend fun addTransaction(draft: TransactionDraft): Boolean {
        val validation = validateTransactionDraft(draft)
        require(validation.isValid) { validation.message ?: "Invalid transaction." }
        return transactionDao.insert(draft.toEntity(ownerId.value)) != -1L
    }

    suspend fun updateTransaction(id: Long, draft: TransactionDraft): Boolean {
        val validation = validateTransactionDraft(draft)
        require(validation.isValid) { validation.message ?: "Invalid transaction." }
        return transactionDao.update(draft.toEntity(ownerId.value, id)) > 0
    }

    suspend fun deleteTransaction(id: Long): Boolean = transactionDao.deleteById(id, ownerId.value) > 0

    suspend fun setBudget(category: FinanceCategory, limitMinor: Long) {
        require(category in FinanceCategory.forType(TransactionType.EXPENSE)) { "Choose an expense category." }
        require(limitMinor > 0L) { "Enter a budget greater than zero." }
        budgetDao.upsert(BudgetEntity(ownerId = ownerId.value, categoryKey = category.key, limitMinor = limitMinor))
    }

    suspend fun deleteBudget(category: FinanceCategory): Boolean =
        budgetDao.deleteByCategory(category.key, ownerId.value) > 0

    private fun TransactionDraft.toEntity(ownerId: String, id: Long = 0L) = TransactionEntity(
        id = id,
        type = type.name,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        categoryKey = category.key,
        description = description,
        accountId = account.id,
        accountName = account.name,
        destinationAccountId = destinationAccount?.id,
        destinationAccountName = destinationAccount?.name,
        timestampMillis = timestampMillis,
        requestId = requestId,
        ownerId = ownerId,
    )

    private fun TransactionEntity.withoutDatabaseIdentity() = copy(id = 0, ownerId = "")
}
