package com.sandbox.vault.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sandbox.vault.data.db.entities.InstalledAppEntity

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_apps ORDER BY installTime DESC")
    suspend fun getAllApps(): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): InstalledAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: InstalledAppEntity)

    @Delete
    suspend fun deleteApp(app: InstalledAppEntity)

    @Query("DELETE FROM installed_apps WHERE packageName = :packageName")
    suspend fun deleteAppByPackageName(packageName: String)
}
