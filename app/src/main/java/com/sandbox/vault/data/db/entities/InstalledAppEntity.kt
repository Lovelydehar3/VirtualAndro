package com.sandbox.vault.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val installTime: Long,
    val version: String,
    val iconPath: String? = null,
    val threatLevel: String = "SAFE"
)
