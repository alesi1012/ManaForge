package com.example.manaforge

import com.example.manaforge.Api.ScryfallApi
import com.example.manaforge.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideAuthRepository(supabase: SupabaseClient) = AuthRepository(supabase)

    @Provides @Singleton
    fun provideDeckRepository(supabase: SupabaseClient) = DeckRepository(supabase)

    @Provides @Singleton
    fun provideCardRepository(api: ScryfallApi, supabase: SupabaseClient) =
        CardRepository(api, supabase)

    @Provides @Singleton
    fun provideStatsRepository(supabase: SupabaseClient) = StatsRepository(supabase)

    @Provides @Singleton
    fun provideMatchRepository(supabase: SupabaseClient, stats: StatsRepository) =
        MatchRepository(supabase, stats)
}