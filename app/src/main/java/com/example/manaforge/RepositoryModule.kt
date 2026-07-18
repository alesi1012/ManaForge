package com.example.manaforge

import android.content.Context
import com.example.manaforge.Api.ScryfallApi
import com.example.manaforge.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideAuthRepository(
        supabase: SupabaseClient,
        @ApplicationContext context: Context
    ) = AuthRepository(supabase, context)

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

    @Provides @Singleton
    fun provideCollectionRepository(supabase: SupabaseClient, cards: CardRepository) =
        CollectionRepository(supabase, cards)
}