package com.sandbox.vault.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.sandbox.vault.core.workprofile.SandboxAppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StoredApkFile(
    val fileName: String,
    val displayName: String,
    val uri: Uri,
    val sizeBytes: Long,
    val lastModified: Long
)

@Singleton
class SandboxApkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val apkDirectory: File by lazy {
        File(context.cacheDir, "sandbox_apks").apply { mkdirs() }
    }

    suspend fun importFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "picked_${System.currentTimeMillis()}.apk"
        val target = createUniqueTargetFile(displayName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open selected APK")
        target
    }

    suspend fun cloneInstalledApk(packageName: String): File = withContext(Dispatchers.IO) {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val sourcePath = appInfo.publicSourceDir ?: appInfo.sourceDir ?: error("APK path not available")
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) error("Installed APK file not found")

        val appLabel = context.packageManager.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        val target = createUniqueTargetFile("$appLabel.apk")
        sourceFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    }

    fun listStoredApks(): List<StoredApkFile> {
        return apkDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                StoredApkFile(
                    fileName = file.name,
                    displayName = file.name,
                    uri = buildUriForFile(file),
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
    }

    fun listCloneCandidates(): List<SandboxAppInfo> {
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val activities = context.packageManager.queryIntentActivities(launcherIntent, 0)
        return activities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val appInfo = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return@mapNotNull null
                if ((appInfo.publicSourceDir ?: appInfo.sourceDir).isNullOrBlank()) return@mapNotNull null
                SandboxAppInfo(
                    packageName = packageName,
                    appName = resolveInfo.loadLabel(context.packageManager)?.toString().orEmpty().ifBlank { packageName },
                    installed = true,
                    launchable = true
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    fun buildUriForFile(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun findFile(fileName: String): File? =
        File(apkDirectory, fileName).takeIf { it.exists() && it.isFile }

    fun deleteStoredApk(fileName: String): Boolean =
        findFile(fileName)?.delete() == true

    fun clearAllStoredApks() {
        apkDirectory.listFiles().orEmpty().forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun createUniqueTargetFile(rawName: String): File {
        val safeBase = sanitizeFileName(rawName)
        val normalized = if (safeBase.endsWith(".apk", ignoreCase = true)) safeBase else "$safeBase.apk"
        val baseName = normalized.removeSuffix(".apk")
        var candidate = File(apkDirectory, normalized)
        var index = 1
        while (candidate.exists()) {
            candidate = File(apkDirectory, "$baseName-$index.apk")
            index += 1
        }
        return candidate
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "sandbox_apk" }

    private companion object
}
