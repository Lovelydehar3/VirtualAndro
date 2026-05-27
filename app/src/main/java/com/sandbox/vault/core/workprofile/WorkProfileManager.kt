package com.sandbox.vault.core.workprofile

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.UserManager
import com.sandbox.vault.admin.SandboxDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PolicyApplyResult(
    val applied: Boolean,
    val messages: List<String>,
    val errors: List<String>
)

data class SandboxAppInfo(
    val packageName: String,
    val appName: String,
    val installed: Boolean,
    val launchable: Boolean,
    val isRecommended: Boolean = false,
    val summary: String? = null
)

@Singleton
class WorkProfileManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val dpm: DevicePolicyManager =
        appContext.getSystemService(DevicePolicyManager::class.java)
    private val packageManager: PackageManager = appContext.packageManager

    val admin: ComponentName =
        ComponentName(appContext, SandboxDeviceAdminReceiver::class.java)

    fun isProfileOwner(): Boolean = dpm.isProfileOwnerApp(appContext.packageName)

    fun isDeviceAdminActive(): Boolean = dpm.isAdminActive(admin)

    fun buildProvisionManagedProfileIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
        }

    fun applyDefaultPolicies(): PolicyApplyResult {
        if (!isProfileOwner()) {
            return PolicyApplyResult(
                applied = false,
                messages = emptyList(),
                errors = listOf("Open the work-profile copy of VirtualAndro to apply profile-owner policies.")
            )
        }

        val messages = mutableListOf<String>()
        val errors = mutableListOf<String>()

        fun applyPolicy(label: String, block: () -> Unit) {
            try {
                block()
                messages += label
            } catch (e: RuntimeException) {
                errors += "$label failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        applyPolicy("Profile named VirtualAndro") {
            dpm.setProfileName(admin, "VirtualAndro")
        }
        applyPolicy("Future runtime permission requests denied by default") {
            dpm.setPermissionPolicy(admin, DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY)
        }
        applyPolicy("Camera disabled inside the work profile") {
            dpm.setCameraDisabled(admin, true)
        }
        applyPolicy("Screenshots disabled inside the work profile") {
            dpm.setScreenCaptureDisabled(admin, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            applyPolicy("Organization label set") {
                dpm.setOrganizationName(admin, "VirtualAndro")
            }
            applyPolicy("Personal contacts hidden from sandbox apps") {
                dpm.setCrossProfileContactsSearchDisabled(admin, true)
            }
            applyPolicy("Personal caller ID hidden from sandbox apps") {
                dpm.setCrossProfileCallerIdDisabled(admin, true)
            }
        }
        legacyBlockingRestrictions().forEach { restriction ->
            applyPolicy(restriction.label) {
                dpm.clearUserRestriction(admin, restriction.key)
            }
        }
        applyPolicy("Play Store hidden inside the sandbox") {
            val hidden = dpm.setApplicationHidden(admin, PLAY_STORE_PACKAGE, true)
            if (!hidden && isPackageInstalled(PLAY_STORE_PACKAGE)) {
                error("Android refused to hide Play Store in the managed profile.")
            }
        }
        applyPolicy("Google app hidden inside the sandbox") {
            val hidden = dpm.setApplicationHidden(admin, GOOGLE_APP_PACKAGE, true)
            if (!hidden && isPackageInstalled(GOOGLE_APP_PACKAGE)) {
                error("Android refused to hide Google in the managed profile.")
            }
        }
        applyPolicy("Chrome enabled inside the sandbox") {
            dpm.enableSystemApp(admin, CHROME_PACKAGE)
        }
        packageInstallerPackages().forEach { packageName ->
            applyPolicy("Package installer enabled: $packageName") {
                dpm.enableSystemApp(admin, packageName)
                dpm.setApplicationHidden(admin, packageName, false)
            }
        }

        profileRestrictions().forEach { restriction ->
            applyPolicy(restriction.label) {
                dpm.addUserRestriction(admin, restriction.key)
            }
        }

        applyPolicy("Managed profile enabled") {
            dpm.setProfileEnabled(admin)
        }

        return PolicyApplyResult(
            applied = errors.isEmpty(),
            messages = messages,
            errors = errors
        )
    }

    fun getSandboxApps(): List<SandboxAppInfo> {
        return queryLauncherActivities()
            .mapNotNull(::resolveSandboxApp)
            .filterNot {
                it.packageName == appContext.packageName ||
                    it.packageName == PLAY_STORE_PACKAGE ||
                    it.packageName == GOOGLE_APP_PACKAGE
            }
            .distinctBy { it.packageName }
            .map { app ->
                if (app.packageName == CHROME_PACKAGE) {
                    app.copy(
                        isRecommended = true,
                        summary = "Real Chrome installed in this sandbox."
                    )
                } else {
                    app
                }
            }
            .sortedWith(compareBy<SandboxAppInfo>({ if (it.isRecommended) 0 else 1 }, { it.appName.lowercase() }))
    }

    fun buildLaunchAppIntent(packageName: String): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun buildInstallApkIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri("sandbox_apk", uri)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

    fun buildUnknownSourcesSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            )
        } else {
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
        }

    fun buildPrivateDnsSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun wipeManagedProfile(activity: Activity) {
        if (isProfileOwner()) {
            dpm.wipeData(0)
        } else {
            activity.finish()
        }
    }

    private fun profileRestrictions(): List<ProfileRestriction> = buildList {
        add(ProfileRestriction(UserManager.DISALLOW_SHARE_LOCATION, "Location sharing blocked"))
        add(ProfileRestriction(UserManager.DISALLOW_CONFIG_LOCATION, "Location settings changes blocked"))
        add(ProfileRestriction(UserManager.DISALLOW_SMS, "SMS blocked in the sandbox profile"))
        add(ProfileRestriction(UserManager.DISALLOW_OUTGOING_CALLS, "Outgoing calls blocked in the sandbox profile"))
        add(ProfileRestriction(UserManager.DISALLOW_UNMUTE_MICROPHONE, "Microphone unmute blocked"))
        add(ProfileRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS, "Account changes blocked"))
        add(ProfileRestriction(UserManager.DISALLOW_CONFIG_CREDENTIALS, "Credential changes blocked"))
        add(ProfileRestriction(UserManager.DISALLOW_AUTOFILL, "Autofill blocked in the sandbox profile"))
        add(ProfileRestriction(UserManager.DISALLOW_BLUETOOTH_SHARING, "Bluetooth sharing blocked"))
        add(ProfileRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER, "USB file transfer blocked"))
        add(ProfileRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, "Debugging features blocked"))
    }

    private fun legacyBlockingRestrictions(): List<ProfileRestriction> = buildList {
        add(ProfileRestriction(UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE, "Cross-profile clipboard allowed"))
        add(ProfileRestriction(UserManager.DISALLOW_INSTALL_APPS, "App installation allowed"))
        add(ProfileRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, "APK sideloading allowed"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(ProfileRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, "Global APK sideload block cleared"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(ProfileRestriction(UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE, "Sharing APKs into sandbox allowed"))
        }
    }

    private fun packageInstallerPackages(): List<String> = listOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller"
    )

    private data class ProfileRestriction(
        val key: String,
        val label: String
    )

    private fun queryLauncherActivities(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun resolveSandboxApp(resolveInfo: ResolveInfo): SandboxAppInfo? {
        val packageName = resolveInfo.activityInfo?.packageName ?: return null
        val appName = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank { packageName }
        return SandboxAppInfo(
            packageName = packageName,
            appName = appName,
            installed = true,
            launchable = true
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
