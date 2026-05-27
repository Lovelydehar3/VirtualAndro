package com.sandbox.vault.data.db.entities

import androidx.room.Entity

@Entity(tableName = "app_permissions", primaryKeys = ["packageName", "permissionName"])
data class PermissionEntity(
    val packageName: String,
    val permissionName: String,
    val allowed: Boolean
)
