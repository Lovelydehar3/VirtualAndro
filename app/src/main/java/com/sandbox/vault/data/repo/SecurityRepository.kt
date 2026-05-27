package com.sandbox.vault.data.repo

import com.sandbox.vault.data.db.NetworkLogDao
import com.sandbox.vault.data.db.PermissionDao
import com.sandbox.vault.data.db.entities.NetworkLogEntity
import com.sandbox.vault.data.db.entities.PermissionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRepository @Inject constructor(
    private val permissionDao: PermissionDao,
    private val networkLogDao: NetworkLogDao
) {
    suspend fun getPermission(packageName: String, permissionName: String): PermissionEntity? = withContext(Dispatchers.IO) {
        permissionDao.getPermission(packageName, permissionName)
    }

    suspend fun getPermissionsForApp(packageName: String): List<PermissionEntity> = withContext(Dispatchers.IO) {
        permissionDao.getPermissionsForApp(packageName)
    }

    suspend fun savePermission(permission: PermissionEntity) = withContext(Dispatchers.IO) {
        permissionDao.insertPermission(permission)
    }

    suspend fun clearPermissionsForApp(packageName: String) = withContext(Dispatchers.IO) {
        permissionDao.deletePermissionsForApp(packageName)
    }

    suspend fun getNetworkLogs(): List<NetworkLogEntity> = withContext(Dispatchers.IO) {
        networkLogDao.getAllLogs()
    }

    suspend fun getNetworkLogsForApp(packageName: String): List<NetworkLogEntity> = withContext(Dispatchers.IO) {
        networkLogDao.getLogsForApp(packageName)
    }

    suspend fun logNetworkRequest(log: NetworkLogEntity) = withContext(Dispatchers.IO) {
        networkLogDao.insertLog(log)
    }

    suspend fun clearAllLogs() = withContext(Dispatchers.IO) {
        networkLogDao.clearAllLogs()
    }

    suspend fun clearLogsForApp(packageName: String) = withContext(Dispatchers.IO) {
        networkLogDao.clearLogsForApp(packageName)
    }
}
