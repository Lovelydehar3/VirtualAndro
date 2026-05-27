package com.sandbox.vault.core.security

import com.sandbox.vault.data.db.SandboxDatabase
import com.sandbox.vault.data.db.entities.NetworkLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    private val db: SandboxDatabase
) {
    suspend fun logNetworkCall(
        packageName: String,
        url: String,
        method: String,
        responseCode: Int,
        bytesSent: Long,
        bytesReceived: Long,
        allowed: Boolean
    ): NetworkLogEntity {
        val log = NetworkLogEntity(
            packageName = packageName,
            url = url,
            method = method,
            timestamp = System.currentTimeMillis(),
            responseCode = responseCode,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
            allowed = allowed
        )
        db.networkLogDao().insertLog(log)
        return log
    }
}
