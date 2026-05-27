package com.sandbox.vault.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SandboxStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sandboxRoot: File
        get() = File(context.filesDir, "sandbox_fs")

    init {
        if (!sandboxRoot.exists()) {
            sandboxRoot.mkdirs()
        }
    }

    fun getAppDataDir(packageName: String): File =
        File(sandboxRoot, "data/$packageName").also { it.mkdirs() }

    fun getAppExternalDir(packageName: String): File =
        File(sandboxRoot, "external/$packageName").also { it.mkdirs() }

    fun getAppSize(packageName: String): Long =
        getAppDataDir(packageName).walkTopDown().sumOf { it.length() } +
                getAppExternalDir(packageName).walkTopDown().sumOf { it.length() }

    fun deleteApp(packageName: String) {
        getAppDataDir(packageName).deleteRecursively()
        getAppExternalDir(packageName).deleteRecursively()
    }
    
    fun getTotalUsage(): Long = if (sandboxRoot.exists()) {
        sandboxRoot.walkTopDown().sumOf { it.length() }
    } else {
        0L
    }

    fun clearAllData() {
        if (sandboxRoot.exists()) {
            sandboxRoot.deleteRecursively()
            sandboxRoot.mkdirs()
        }
    }
}
