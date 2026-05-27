package com.sandbox.vault.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_logs")
data class NetworkLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val url: String,
    val method: String,
    val timestamp: Long,
    val responseCode: Int,
    val bytesSent: Long,
    val bytesReceived: Long,
    val allowed: Boolean = true
)
