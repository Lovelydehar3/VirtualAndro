package com.sandbox.vault.ui.workprofile

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandbox.vault.core.security.ApkScanner
import com.sandbox.vault.core.security.ScanResult
import com.sandbox.vault.core.storage.SandboxApkManager
import com.sandbox.vault.core.storage.StoredApkFile
import com.sandbox.vault.core.workprofile.SandboxAppInfo
import com.sandbox.vault.core.workprofile.WorkProfileManager
import com.sandbox.vault.data.repo.PasswordVerificationResult
import com.sandbox.vault.data.repo.SandboxLockRepository
import com.sandbox.vault.data.repo.SandboxSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkProfileUiState(
    val isProfileOwner: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val isScanning: Boolean = false,
    val selectedApkUri: Uri? = null,
    val selectedApkName: String? = null,
    val scanResult: ScanResult? = null,
    val sandboxApps: List<SandboxAppInfo> = emptyList(),
    val cloneCandidates: List<SandboxAppInfo> = emptyList(),
    val storedApks: List<StoredApkFile> = emptyList(),
    val lockConfigured: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val isUnlocked: Boolean = true,
    val failedPasswordAttempts: Int = 0,
    val lockoutRemainingMillis: Long = 0L,
    val dnsHelperEnabled: Boolean = false,
    val policyMessages: List<String> = emptyList(),
    val policyErrors: List<String> = emptyList(),
    val error: String? = null,
    val infoMessage: String? = null
) {
    val hasPreparedApk: Boolean
        get() = selectedApkUri != null && scanResult != null
}

@HiltViewModel
class WorkProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workProfileManager: WorkProfileManager,
    private val apkScanner: ApkScanner,
    private val sandboxLockRepository: SandboxLockRepository,
    private val sandboxSettingsRepository: SandboxSettingsRepository,
    private val sandboxApkManager: SandboxApkManager
) : ViewModel() {

    private val _state = MutableStateFlow(WorkProfileUiState())
    val state: StateFlow<WorkProfileUiState> = _state.asStateFlow()
    private var lockoutCountdownJob: Job? = null
    private var policiesAppliedOnce = false

    init {
        observeLockSettings()
        observeSandboxSettings()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val isProfileOwner = workProfileManager.isProfileOwner()
            var policyMessages = _state.value.policyMessages
            var policyErrors = _state.value.policyErrors
            if (isProfileOwner && !policiesAppliedOnce) {
                val policyResult = workProfileManager.applyDefaultPolicies()
                policiesAppliedOnce = true
                policyMessages = policyResult.messages
                policyErrors = policyResult.errors
            }
            val apps = withContext(Dispatchers.IO) {
                if (isProfileOwner) workProfileManager.getSandboxApps() else emptyList()
            }
            val cloneCandidates = withContext(Dispatchers.IO) {
                if (isProfileOwner) sandboxApkManager.listCloneCandidates() else emptyList()
            }
            val storedApks = withContext(Dispatchers.IO) { sandboxApkManager.listStoredApks() }
            _state.update {
                it.copy(
                    isProfileOwner = isProfileOwner,
                    isDeviceAdminActive = workProfileManager.isDeviceAdminActive(),
                    sandboxApps = apps,
                    cloneCandidates = cloneCandidates,
                    storedApks = storedApks,
                    policyMessages = policyMessages,
                    policyErrors = policyErrors,
                    error = policyErrors.firstOrNull()
                )
            }
        }
    }

    fun provisioningIntent(): Intent = workProfileManager.buildProvisionManagedProfileIntent()

    fun unknownSourcesSettingsIntent(): Intent = workProfileManager.buildUnknownSourcesSettingsIntent()

    fun privateDnsSettingsIntent(): Intent = workProfileManager.buildPrivateDnsSettingsIntent()

    fun launchIntentForApp(packageName: String): Intent? = workProfileManager.buildLaunchAppIntent(packageName)

    fun installIntentForSelectedApk(): Intent? =
        _state.value.selectedApkUri?.let(workProfileManager::buildInstallApkIntent)

    fun applyPolicies() {
        val result = workProfileManager.applyDefaultPolicies()
        _state.update {
            it.copy(
                isProfileOwner = workProfileManager.isProfileOwner(),
                isDeviceAdminActive = workProfileManager.isDeviceAdminActive(),
                policyMessages = result.messages,
                policyErrors = result.errors,
                error = result.errors.firstOrNull()
            )
        }
        refresh()
    }

    fun importPickedApk(uri: Uri) {
        viewModelScope.launch {
            runPreparedApkFlow(
                actionLabel = "Importing APK",
                block = { sandboxApkManager.importFromUri(uri) }
            )
        }
    }

    fun prepareStoredApk(fileName: String) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { sandboxApkManager.findFile(fileName) }
            if (file == null) {
                showError("APK file was not found.")
                refresh()
                return@launch
            }
            scanPreparedFile(file)
        }
    }

    fun cloneInstalledApk(packageName: String) {
        viewModelScope.launch {
            runPreparedApkFlow(
                actionLabel = "Cloning installed APK",
                block = { sandboxApkManager.cloneInstalledApk(packageName) }
            )
        }
    }

    fun deleteStoredApk(fileName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sandboxApkManager.deleteStoredApk(fileName) }
            if (_state.value.selectedApkName == fileName) {
                clearSelectedApk(deletePreparedFile = false)
            }
            refresh()
        }
    }

    fun clearSelectedApk(deletePreparedFile: Boolean = true) {
        val selectedFileName = _state.value.selectedApkName
        viewModelScope.launch {
            if (deletePreparedFile && selectedFileName != null) {
                withContext(Dispatchers.IO) { sandboxApkManager.deleteStoredApk(selectedFileName) }
            }
            _state.update {
                it.copy(
                    selectedApkUri = null,
                    selectedApkName = null,
                    scanResult = null,
                    error = null
                )
            }
            refresh()
        }
    }

    fun completeInstallFlow() {
        clearSelectedApk(deletePreparedFile = true)
    }

    fun wipeManagedProfile(activity: Activity) {
        workProfileManager.wipeManagedProfile(activity)
    }

    fun savePassword(password: String, confirmPassword: String, enableBiometric: Boolean) {
        val trimmedPassword = password.trim()
        val trimmedConfirm = confirmPassword.trim()
        when {
            trimmedPassword.length < 8 -> {
                showError("Password must be at least 8 characters.")
            }
            trimmedPassword != trimmedConfirm -> {
                showError("Password confirmation does not match.")
            }
            else -> {
                viewModelScope.launch {
                    sandboxLockRepository.savePassword(
                        password = trimmedPassword,
                        biometricEnabled = enableBiometric && _state.value.biometricAvailable
                    )
                    _state.update {
                        it.copy(
                            isUnlocked = true,
                            error = null
                        )
                    }
                }
            }
        }
    }

    fun unlockWithPassword(password: String) {
        val candidate = password.trim()
        if (candidate.isEmpty()) {
            showError("Enter your sandbox password.")
            return
        }

        viewModelScope.launch {
            val result = sandboxLockRepository.verifyPassword(candidate)
            handlePasswordVerification(result)
        }
    }

    fun onBiometricAuthenticated() {
        if (_state.value.lockoutRemainingMillis == 0L) {
            _state.update { it.copy(isUnlocked = true, error = null) }
        }
    }

    fun onBiometricError(message: String) {
        showError(message)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (_state.value.lockConfigured) {
                sandboxLockRepository.setBiometricEnabled(enabled && _state.value.biometricAvailable)
            }
        }
    }

    fun clearPassword() {
        viewModelScope.launch {
            sandboxLockRepository.clearPassword()
            _state.update {
                it.copy(
                    isUnlocked = true,
                    error = null
                )
            }
        }
    }

    fun lockSandbox() {
        _state.update {
            if (it.lockConfigured) it.copy(isUnlocked = false) else it
        }
    }

    fun setDnsHelperEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sandboxSettingsRepository.setDnsHelperEnabled(enabled)
        }
    }

    fun copyDnsHostToClipboard() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Private DNS host", PRIVATE_DNS_HOST))
        _state.update { it.copy(infoMessage = "Copied $PRIVATE_DNS_HOST") }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInfoMessage() {
        _state.update { it.copy(infoMessage = null) }
    }

    private fun observeLockSettings() {
        viewModelScope.launch {
            sandboxLockRepository.settings.collect { settings ->
                val biometricAvailable = isBiometricAvailable()
                val lockoutRemainingMillis = maxOf(0L, settings.lockedUntilEpochMillis - System.currentTimeMillis())
                _state.update { current ->
                    current.copy(
                        lockConfigured = settings.isConfigured,
                        biometricEnabled = settings.isConfigured && settings.biometricEnabled && biometricAvailable,
                        biometricAvailable = biometricAvailable,
                        isUnlocked = when {
                            !settings.isConfigured -> true
                            lockoutRemainingMillis > 0L -> false
                            else -> current.isUnlocked
                        },
                        failedPasswordAttempts = settings.failedAttempts,
                        lockoutRemainingMillis = lockoutRemainingMillis
                    )
                }
                syncLockoutCountdown(settings.lockedUntilEpochMillis)
            }
        }
    }

    private fun observeSandboxSettings() {
        viewModelScope.launch {
            sandboxSettingsRepository.settings.collect { settings ->
                _state.update { it.copy(dnsHelperEnabled = settings.dnsHelperEnabled) }
            }
        }
    }

    private suspend fun runPreparedApkFlow(
        actionLabel: String,
        block: suspend () -> File
    ) {
        _state.update {
            it.copy(
                isScanning = true,
                error = null,
                infoMessage = actionLabel
            )
        }
        try {
            val file = block()
            scanPreparedFile(file)
            refresh()
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isScanning = false,
                    error = "$actionLabel failed: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }

    private suspend fun scanPreparedFile(file: File) {
        val result = withContext(Dispatchers.IO) {
            apkScanner.scan(file.absolutePath)
        }
        val contentUri = sandboxApkManager.buildUriForFile(file)
        _state.update {
            it.copy(
                isScanning = false,
                selectedApkUri = contentUri,
                selectedApkName = file.name,
                scanResult = result,
                error = null
            )
        }
    }

    private fun isBiometricAvailable(): Boolean {
        val result = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    private fun handlePasswordVerification(result: PasswordVerificationResult) {
        _state.update { current ->
            when {
                result.success -> current.copy(
                    isUnlocked = true,
                    error = null
                )
                result.lockedUntilEpochMillis > System.currentTimeMillis() -> current.copy(
                    isUnlocked = false,
                    error = "Too many failed attempts. Unlock is blocked for ${formatLockout(result.lockedUntilEpochMillis - System.currentTimeMillis())}.",
                    failedPasswordAttempts = result.failedAttempts,
                    lockoutRemainingMillis = maxOf(0L, result.lockedUntilEpochMillis - System.currentTimeMillis())
                )
                else -> current.copy(
                    isUnlocked = false,
                    error = "Incorrect sandbox password.",
                    failedPasswordAttempts = result.failedAttempts
                )
            }
        }
    }

    private fun syncLockoutCountdown(lockedUntilEpochMillis: Long) {
        lockoutCountdownJob?.cancel()
        if (lockedUntilEpochMillis <= System.currentTimeMillis()) {
            _state.update { it.copy(lockoutRemainingMillis = 0L) }
            return
        }

        lockoutCountdownJob = viewModelScope.launch {
            while (true) {
                val remaining = maxOf(0L, lockedUntilEpochMillis - System.currentTimeMillis())
                _state.update { current ->
                    current.copy(
                        lockoutRemainingMillis = remaining,
                        isUnlocked = if (remaining > 0L) false else current.isUnlocked,
                        error = if (remaining > 0L && current.error?.startsWith("Too many failed attempts") == true) {
                            "Too many failed attempts. Unlock is blocked for ${formatLockout(remaining)}."
                        } else {
                            current.error
                        }
                    )
                }
                if (remaining == 0L) break
                delay(1_000L)
            }
            _state.update { current ->
                current.copy(
                    lockoutRemainingMillis = 0L,
                    error = if (current.error?.startsWith("Too many failed attempts") == true) null else current.error
                )
            }
        }
    }

    private fun formatLockout(durationMillis: Long): String {
        val totalSeconds = maxOf(0L, durationMillis / 1_000L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        const val PRIVATE_DNS_HOST = "dns.adguard.com"
    }
}
