package com.expensemanager.android.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId ORDER BY categoryKey")
    fun observeAll(ownerId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE ownerId = :ownerId ORDER BY categoryKey")
    suspend fun getAll(ownerId: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryKey = :categoryKey AND ownerId = :ownerId")
    suspend fun deleteByCategory(categoryKey: String, ownerId: String): Int

    @Query("DELETE FROM budgets WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String): Int

    @Query("UPDATE budgets SET ownerId = :ownerId WHERE ownerId = :localOwnerId AND NOT EXISTS (SELECT 1 FROM budgets WHERE ownerId = :ownerId)")
    suspend fun claimLocalBudgets(ownerId: String, localOwnerId: String): Int
}
