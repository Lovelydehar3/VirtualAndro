package com.sandbox.vault.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sandboxSettingsDataStore by preferencesDataStore(name = "sandbox_settings")

data class SandboxSettings(
    val dnsHelperEnabled: Boolean = false
)

@Singleton
class SandboxSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dnsHelperEnabledKey = booleanPreferencesKey("dns_helper_enabled")

    val settings: Flow<SandboxSettings> = context.sandboxSettingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            SandboxSettings(
                dnsHelperEnabled = preferences[dnsHelperEnabledKey] ?: false
            )
        }

    suspend fun setDnsHelperEnabled(enabled: Boolean) {
        context.sandboxSettingsDataStore.edit { preferences ->
            preferences[dnsHelperEnabledKey] = enabled
        }
    }
}
