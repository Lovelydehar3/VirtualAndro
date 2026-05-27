package com.sandbox.vault.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sandbox.vault.data.db.entities.PermissionEntity

@Dao
interface PermissionDao {
    @Query("SELECT * FROM app_permissions WHERE packageName = :packageName AND permissionName = :permissionName")
    suspend fun getPermission(packageName: String, permissionName: String): PermissionEntity?

    @Query("SELECT * FROM app_permissions WHERE packageName = :packageName")
    suspend fun getPermissionsForApp(packageName: String): List<PermissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermission(permission: PermissionEntity)

    @Query("DELETE FROM app_permissions WHERE packageName = :packageName")
    suspend fun deletePermissionsForApp(packageName: String)
}
