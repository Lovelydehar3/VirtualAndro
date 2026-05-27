package com.sandbox.vault.data.repo

import com.sandbox.vault.data.db.InstalledAppDao
import com.sandbox.vault.data.db.entities.InstalledAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val appDao: InstalledAppDao
) {
    suspend fun getInstalledApps(): List<InstalledAppEntity> = withContext(Dispatchers.IO) {
        appDao.getAllApps()
    }

    suspend fun saveApp(app: InstalledAppEntity) = withContext(Dispatchers.IO) {
        appDao.insertApp(app)
    }

    suspend fun removeApp(packageName: String) = withContext(Dispatchers.IO) {
        appDao.deleteAppByPackageName(packageName)
    }
    
    suspend fun getApp(packageName: String): InstalledAppEntity? = withContext(Dispatchers.IO) {
        appDao.getAppByPackageName(packageName)
    }
}
