package com.sandbox.vault.di

import android.content.Context
import androidx.room.Room
import com.sandbox.vault.data.db.InstalledAppDao
import com.sandbox.vault.data.db.NetworkLogDao
import com.sandbox.vault.data.db.PermissionDao
import com.sandbox.vault.data.db.SandboxDatabase
import com.sandbox.vault.data.repo.AppRepository
import com.sandbox.vault.data.repo.SecurityRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SandboxDatabase {
        return Room.databaseBuilder(
            context,
            SandboxDatabase::class.java,
            "sandbox_vault.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideInstalledAppDao(db: SandboxDatabase): InstalledAppDao {
        return db.installedAppDao()
    }

    @Provides
    @Singleton
    fun providePermissionDao(db: SandboxDatabase): PermissionDao {
        return db.permissionDao()
    }

    @Provides
    @Singleton
    fun provideNetworkLogDao(db: SandboxDatabase): NetworkLogDao {
        return db.networkLogDao()
    }

    @Provides
    @Singleton
    fun provideAppRepository(installedAppDao: InstalledAppDao): AppRepository {
        return AppRepository(installedAppDao)
    }

    @Provides
    @Singleton
    fun provideSecurityRepository(
        permissionDao: PermissionDao,
        networkLogDao: NetworkLogDao
    ): SecurityRepository {
        return SecurityRepository(permissionDao, networkLogDao)
    }
}
