package com.expensemanager.android.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensemanager.android.core.data.FinanceRepository
import com.expensemanager.android.core.data.BackupRestoreResult
import com.expensemanager.android.core.model.CategoryBudget
import com.expensemanager.android.core.model.FinanceCategory
import com.expensemanager.android.core.model.TransactionDraft
import com.expensemanager.android.core.model.FinanceTransaction
import com.expensemanager.android.core.model.validateTransactionDraft
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FinanceEvent {
    data object TransactionSaved : FinanceEvent
    data object TransactionUpdated : FinanceEvent
    data object TransactionDeleted : FinanceEvent
    data object BudgetSaved : FinanceEvent
    data object BudgetDeleted : FinanceEvent
    data class Error(val message: String) : FinanceEvent
}

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {
    val transactions: StateFlow<List<FinanceTransaction>> = repository.transactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val budgets: StateFlow<List<CategoryBudget>> = repository.budgets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _events = MutableSharedFlow<FinanceEvent>()
    val events = _events.asSharedFlow()

    fun saveTransaction(draft: TransactionDraft) {
        if (_isSaving.value) return
        val validation = validateTransactionDraft(draft)
        if (!validation.isValid) {
            viewModelScope.launch {
                _events.emit(FinanceEvent.Error(validation.message ?: "Invalid transaction."))
            }
            return
        }
        _isSaving.value = true
        viewModelScope.launch {
            runCatching { repository.addTransaction(draft) }
                .onSuccess { saved ->
                    if (saved) _events.emit(FinanceEvent.TransactionSaved)
                    else _events.emit(FinanceEvent.Error("This transaction was already saved."))
                }
                .onFailure { _events.emit(FinanceEvent.Error("Could not save this transaction. Try again.")) }
            _isSaving.value = false
        }
    }

    fun updateTransaction(id: Long, draft: TransactionDraft) {
        if (_isSaving.value) return
        val validation = validateTransactionDraft(draft)
        if (!validation.isValid) {
            viewModelScope.launch { _events.emit(FinanceEvent.Error(validation.message ?: "Invalid transaction.")) }
            return
        }
        _isSaving.value = true
        viewModelScope.launch {
            runCatching { repository.updateTransaction(id, draft) }
                .onSuccess { updated ->
                    if (updated) _events.emit(FinanceEvent.TransactionUpdated)
                    else _events.emit(FinanceEvent.Error("This transaction no longer exists."))
                }
                .onFailure { _events.emit(FinanceEvent.Error("Could not update this transaction. Try again.")) }
            _isSaving.value = false
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteTransaction(id) }
                .onSuccess { deleted ->
                    if (deleted) _events.emit(FinanceEvent.TransactionDeleted)
                    else _events.emit(FinanceEvent.Error("This transaction no longer exists."))
                }
                .onFailure { _events.emit(FinanceEvent.Error("Could not delete this transaction. Try again.")) }
        }
    }

    fun setBudget(category: FinanceCategory, limitMinor: Long) {
        viewModelScope.launch {
            runCatching { repository.setBudget(category, limitMinor) }
                .onSuccess { _events.emit(FinanceEvent.BudgetSaved) }
                .onFailure { error ->
                    _events.emit(FinanceEvent.Error(error.message ?: "Could not save this budget."))
                }
        }
    }

    fun deleteBudget(category: FinanceCategory) {
        viewModelScope.launch {
            runCatching { repository.deleteBudget(category) }
                .onSuccess { deleted ->
                    if (deleted) _events.emit(FinanceEvent.BudgetDeleted)
                    else _events.emit(FinanceEvent.Error("This budget no longer exists."))
                }
                .onFailure { _events.emit(FinanceEvent.Error("Could not delete this budget.")) }
        }
    }

    suspend fun createEncryptedBackup(password: String): ByteArray =
        repository.createEncryptedBackup(password)

    suspend fun restoreEncryptedBackup(bytes: ByteArray, password: String): BackupRestoreResult =
        repository.restoreEncryptedBackup(bytes, password)
}

class FinanceViewModelFactory(
    private val repository: FinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FinanceViewModel::class.java))
        return FinanceViewModel(repository) as T
    }
}
