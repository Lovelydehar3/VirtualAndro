package com.sandbox.vault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sandbox.vault.data.db.entities.InstalledAppEntity
import com.sandbox.vault.data.db.entities.NetworkLogEntity
import com.sandbox.vault.data.db.entities.PermissionEntity

@Database(
    entities = [
        InstalledAppEntity::class,
        NetworkLogEntity::class,
        PermissionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SandboxDatabase : RoomDatabase() {
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun permissionDao(): PermissionDao
    abstract fun networkLogDao(): NetworkLogDao
}
