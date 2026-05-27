package com.sandbox.vault.core.security

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ThreatLevel { SAFE, SUSPICIOUS, DANGEROUS }

data class ScanResult(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val declaredPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val threatLevel: ThreatLevel,
    val apkSizeBytes: Long
)

@Singleton
class ApkScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dangerousPerms = setOf(
        "android.permission.READ_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.CAMERA",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.RECEIVE_BOOT_COMPLETED"
    )

    fun scan(apkPath: String): ScanResult {
        val file = File(apkPath)
        val pm = context.packageManager
        val archiveInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS)
        
        val declared = archiveInfo?.requestedPermissions?.toList() ?: emptyList()
        val dangerous = declared.filter { it in dangerousPerms }
        
        val threatLevel = when {
            dangerous.size >= 5 -> ThreatLevel.DANGEROUS
            dangerous.size >= 2 -> ThreatLevel.SUSPICIOUS
            else -> ThreatLevel.SAFE
        }

        // Try to get application label, fallback to filename
        val appName = try {
            archiveInfo?.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
                appInfo.loadLabel(pm).toString()
            } ?: file.nameWithoutExtension
        } catch (e: Exception) {
            file.nameWithoutExtension
        }

        return ScanResult(
            packageName = archiveInfo?.packageName ?: "unknown.package",
            appName = appName,
            versionName = archiveInfo?.versionName ?: "1.0",
            declaredPermissions = declared,
            dangerousPermissions = dangerous,
            threatLevel = threatLevel,
            apkSizeBytes = file.length()
        )
    }
}
