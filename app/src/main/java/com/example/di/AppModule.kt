package com.example.di

import android.app.Application
import android.content.Context
import com.example.data.local.TexForgeDatabase
import com.example.data.local.dao.ProjectDao
import com.example.data.preferences.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    @Singleton
    fun provideDatabase(context: Context): TexForgeDatabase = TexForgeDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideProjectDao(database: TexForgeDatabase): ProjectDao = database.projectDao()

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(context: Context): UserPreferencesRepository =
        UserPreferencesRepository(context)

    @Provides
    @Singleton
    fun provideMapProcessor(): com.example.engine.MapProcessor = com.example.engine.CpuMapProcessor()

    @Provides
    @Singleton
    fun provideOnnxDepthEngine(context: Context): com.example.engine.OnnxDepthEngine =
        com.example.engine.OnnxDepthEngine(context)
}
