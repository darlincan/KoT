package com.neonkaiju.kot.di

import android.content.Context
import com.neonkaiju.kot.BuildConfig
import com.neonkaiju.kot.ai.BotPolicy
import com.neonkaiju.kot.ai.HeuristicBotPolicy
import com.neonkaiju.kot.data.CatalogRepository
import com.neonkaiju.kot.data.DataStoreSettingsRepository
import com.neonkaiju.kot.data.FileGameRepository
import com.neonkaiju.kot.data.GameRepository
import com.neonkaiju.kot.data.ResourceCatalogRepository
import com.neonkaiju.kot.data.SettingsRepository
import com.neonkaiju.kot.feature.game.GameViewModel
import com.neonkaiju.kot.model.SeedProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

/** Dépendances de l'application. Un test peut remplacer ce module (graine fixe, faux repositories). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {


    @Provides
    @Singleton
    fun provideGameRepository(@ApplicationContext context: Context): GameRepository =
        FileGameRepository(File(context.filesDir, "saved_game_v1.json"))

    @Provides
    @Singleton
    fun provideCatalogRepository(): CatalogRepository = ResourceCatalogRepository()

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository.create(File(context.filesDir, "datastore/settings.preferences_pb"))

    @Provides
    fun provideBotPolicy(): BotPolicy = HeuristicBotPolicy()

    @Provides
    fun provideSeedProvider(): SeedProvider = SeedProvider { System.currentTimeMillis() }

    @Provides
    @Named(GameViewModel.APP_VERSION)
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME
}
