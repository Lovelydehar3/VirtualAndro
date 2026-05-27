package com.sandbox.vault.data.repo

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sandboxLockDataStore by preferencesDataStore(name = "sandbox_lock")

data class SandboxLockSettings(
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val biometricEnabled: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMillis: Long = 0L
) {
    val isConfigured: Boolean
        get() = !passwordHash.isNullOrBlank() && !passwordSalt.isNullOrBlank()
}

data class PasswordVerificationResult(
    val success: Boolean,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMillis: Long = 0L
)

@Singleton
class SandboxLockRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val passwordHashKey = stringPreferencesKey("password_hash")
    private val passwordSaltKey = stringPreferencesKey("password_salt")
    private val biometricEnabledKey = booleanPreferencesKey("biometric_enabled")
    private val failedAttemptsKey = intPreferencesKey("failed_attempts")
    private val lockedUntilKey = longPreferencesKey("locked_until")

    val settings: Flow<SandboxLockSettings> = context.sandboxLockDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            SandboxLockSettings(
                passwordHash = preferences[passwordHashKey],
                passwordSalt = preferences[passwordSaltKey],
                biometricEnabled = preferences[biometricEnabledKey] ?: false,
                failedAttempts = preferences[failedAttemptsKey] ?: 0,
                lockedUntilEpochMillis = preferences[lockedUntilKey] ?: 0L
            )
        }

    suspend fun savePassword(password: String, biometricEnabled: Boolean) {
        val salt = ByteArray(SALT_SIZE_BYTES).also(secureRandom::nextBytes)
        val hash = deriveHash(password, salt)
        context.sandboxLockDataStore.edit { preferences ->
            preferences[passwordHashKey] = encode(hash)
            preferences[passwordSaltKey] = encode(salt)
            preferences[biometricEnabledKey] = biometricEnabled
            preferences[failedAttemptsKey] = 0
            preferences[lockedUntilKey] = 0L
        }
    }

    suspend fun verifyPassword(password: String): PasswordVerificationResult {
        val currentSettings = settings.first()
        val now = System.currentTimeMillis()
        val expectedHash = currentSettings.passwordHash
        val salt = currentSettings.passwordSalt

        if (expectedHash.isNullOrBlank() || salt.isNullOrBlank()) {
            return PasswordVerificationResult(success = false)
        }

        if (currentSettings.lockedUntilEpochMillis > now) {
            return PasswordVerificationResult(
                success = false,
                failedAttempts = currentSettings.failedAttempts,
                lockedUntilEpochMillis = currentSettings.lockedUntilEpochMillis
            )
        }

        val candidateHash = deriveHash(password, decode(salt))
        val matches = MessageDigest.isEqual(candidateHash, decode(expectedHash))

        return if (matches) {
            context.sandboxLockDataStore.edit { preferences ->
                preferences[failedAttemptsKey] = 0
                preferences[lockedUntilKey] = 0L
            }
            PasswordVerificationResult(success = true)
        } else {
            val failedAttempts = currentSettings.failedAttempts + 1
            val lockedUntil = lockoutDeadlineForAttempt(failedAttempts, now)
            context.sandboxLockDataStore.edit { preferences ->
                preferences[failedAttemptsKey] = failedAttempts
                preferences[lockedUntilKey] = lockedUntil
            }
            PasswordVerificationResult(
                success = false,
                failedAttempts = failedAttempts,
                lockedUntilEpochMillis = lockedUntil
            )
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.sandboxLockDataStore.edit { preferences ->
            preferences[biometricEnabledKey] = enabled
        }
    }

    suspend fun clearPassword() {
        context.sandboxLockDataStore.edit { preferences ->
            preferences.remove(passwordHashKey)
            preferences.remove(passwordSaltKey)
            preferences.remove(biometricEnabledKey)
            preferences.remove(failedAttemptsKey)
            preferences.remove(lockedUntilKey)
        }
    }

    private fun deriveHash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_SIZE_BITS)
        return try {
            secretKeyFactory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    private fun lockoutDeadlineForAttempt(failedAttempts: Int, now: Long): Long {
        val lockoutMillis = when {
            failedAttempts < 5 -> 0L
            failedAttempts == 5 -> 30_000L
            failedAttempts == 6 -> 120_000L
            else -> 300_000L
        }
        return if (lockoutMillis == 0L) 0L else now + lockoutMillis
    }

    private companion object {
        const val SALT_SIZE_BYTES = 16
        const val HASH_SIZE_BITS = 256
        const val PBKDF2_ITERATIONS = 120_000
        val secureRandom = SecureRandom()
        val secretKeyFactory: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    }
}
