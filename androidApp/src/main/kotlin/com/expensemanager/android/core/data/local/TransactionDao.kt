package com.expensemanager.android.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId ORDER BY timestampMillis DESC")
    fun observeAll(ownerId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId ORDER BY timestampMillis DESC")
    suspend fun getAll(ownerId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :transactionId AND ownerId = :ownerId")
    suspend fun deleteById(transactionId: Long, ownerId: String): Int

    @Query("UPDATE transactions SET ownerId = :ownerId WHERE ownerId = :localOwnerId AND NOT EXISTS (SELECT 1 FROM transactions WHERE ownerId = :ownerId)")
    suspend fun claimLocalTransactions(ownerId: String, localOwnerId: String): Int
}
