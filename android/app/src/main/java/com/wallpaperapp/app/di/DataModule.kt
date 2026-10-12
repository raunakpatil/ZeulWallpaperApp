package com.wallpaperapp.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.wallpaperapp.app.BuildConfig
import com.wallpaperapp.app.data.local.AppDatabase
import com.wallpaperapp.app.data.local.WallpaperDao
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import com.wallpaperapp.app.data.remote.SupabaseWallpaperRemoteDataSource
import com.wallpaperapp.app.data.remote.WallpaperRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /** Fake data when asked for (USE_FAKE_BACKEND) or when local.properties has no Supabase keys yet. */
    val useFakeBackend: Boolean
        get() = BuildConfig.USE_FAKE_BACKEND ||
            BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Postgrest)
            install(Auth) // B7: session saved on device and refreshed automatically
        }

    @Provides
    @Singleton
    fun provideRemoteDataSource(
        real: Provider<SupabaseWallpaperRemoteDataSource>,
        fake: Provider<FakeWallpaperRemoteDataSource>,
    ): WallpaperRemoteDataSource = if (useFakeBackend) fake.get() else real.get()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideWallpaperDao(db: AppDatabase): WallpaperDao = db.wallpaperDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_prefs") }
}
