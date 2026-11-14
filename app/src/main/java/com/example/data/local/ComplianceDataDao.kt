package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComplianceDataDao {
    @Query("SELECT * FROM compliance_snapshots ORDER BY startedAtEpochMs DESC")
    fun observeSnapshots(): Flow<List<ComplianceSnapshotEntity>>

    @Query("SELECT COUNT(*) FROM compliance_snapshots")
    suspend fun snapshotCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<ComplianceSnapshotEntity>)

    @Query("DELETE FROM compliance_snapshots WHERE id = :scanId")
    suspend fun deleteSnapshotById(scanId: String)

    @Query("DELETE FROM compliance_snapshots")
    suspend fun clearSnapshots()

    @Query("SELECT * FROM control_monitors ORDER BY code ASC")
    fun observeMonitors(): Flow<List<ControlMonitorEntity>>

    @Query("SELECT COUNT(*) FROM control_monitors")
    suspend fun monitorCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonitors(monitors: List<ControlMonitorEntity>)

    @Query("DELETE FROM control_monitors")
    suspend fun clearMonitors()
}
