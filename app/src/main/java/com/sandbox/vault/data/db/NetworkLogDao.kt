package com.sandbox.vault.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sandbox.vault.data.db.entities.NetworkLogEntity

@Dao
interface NetworkLogDao {
    @Query("SELECT * FROM network_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<NetworkLogEntity>

    @Query("SELECT * FROM network_logs WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getLogsForApp(packageName: String): List<NetworkLogEntity>

    @Insert
    suspend fun insertLog(log: NetworkLogEntity)

    @Query("DELETE FROM network_logs")
    suspend fun clearAllLogs()
    
    @Query("DELETE FROM network_logs WHERE packageName = :packageName")
    suspend fun clearLogsForApp(packageName: String)
}
