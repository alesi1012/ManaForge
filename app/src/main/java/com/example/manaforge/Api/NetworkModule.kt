package com.example.manaforge.Api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton



private const val SUPABASE_URL = "https://vbpgcnnfujslrozmpuop.supabase.co"
private const val SUPABASE_KEY = "YOUR_PUBLIC_ANON_KEY_HERE"
private const val SCRYFALL_BASE_URL = "https://api.scryfall.com/"



@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {


    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }


    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()


    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(SCRYFALL_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideScryfallApi(retrofit: Retrofit): ScryfallApi =
        retrofit.create(ScryfallApi::class.java)
}