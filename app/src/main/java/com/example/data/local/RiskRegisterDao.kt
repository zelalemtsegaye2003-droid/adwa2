package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RiskRegisterDao {
    @Query("SELECT * FROM risk_register ORDER BY residualScore DESC, code ASC")
    fun observeAll(): Flow<List<RiskRegisterEntity>>

    @Query("SELECT * FROM risk_register WHERE status = :status ORDER BY residualScore DESC, code ASC")
    fun observeByStatus(status: String): Flow<List<RiskRegisterEntity>>

    @Query("SELECT * FROM risk_register WHERE residualScore >= :minScore AND status != 'MITIGATED' ORDER BY residualScore DESC")
    fun observeOpenHighRisks(minScore: Int = 12): Flow<List<RiskRegisterEntity>>

    @Query("SELECT * FROM risk_register WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RiskRegisterEntity?

    @Query("SELECT COUNT(*) FROM risk_register")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RiskRegisterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<RiskRegisterEntity>)

    @Update
    suspend fun update(entry: RiskRegisterEntity)

    @Query("UPDATE risk_register SET status = :status, treatment = :treatment, residualScore = :residualScore, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun updateStatusAndScore(
        id: String,
        status: String,
        treatment: String,
        residualScore: Int,
        updatedAt: Long,
    )

    @Query("DELETE FROM risk_register WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM risk_register")
    suspend fun clearAll()
}
