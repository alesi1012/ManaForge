package com.example.manaforge


import com.example.manaforge.Deck
import com.example.manaforge.DeckFormat
import com.example.manaforge.Result
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ManaForge"

@Singleton
class DeckRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun createDeck(
        userId: Int,
        name: String,
        format: DeckFormat
    ): Result<Deck> = withContext(Dispatchers.IO) {
        try {
            val created = supabase.postgrest["decks"]
                .insert(buildJsonObject {
                    put("user_id", userId)
                    put("name", name)
                    put("format", format.value)
                }) { select() }
                .decodeSingle<DeckDto>()
                .toDeck()

            supabase.postgrest["deck_stats"].insert(
                buildJsonObject { put("deck_id", created.id) }
            )

            Result.Success(created)
        } catch (e: Exception) {
            Result.Error("Could not create deck: ${e.message}", e)
        }
    }

    suspend fun getDecksByUser(userId: Int): Result<List<Deck>> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "getDecksByUser: querying with userId=$userId")
            try {
                val decks = supabase.postgrest["decks"]
                    .select(Columns.ALL) {
                        filter { eq("user_id", userId) }
                        order("updated_at", Order.DESCENDING)
                    }
                    .decodeList<DeckDto>()
                    .map { it.toDeck() }
                Log.d(TAG, "getDecksByUser: got ${decks.size} decks")
                Result.Success(decks)
            } catch (e: Exception) {
                Log.e(TAG, "getDecksByUser failed for userId=$userId", e)
                Result.Error("Could not load decks: ${e.message}", e)
            }
        }

    suspend fun getFeaturedDecks(): Result<List<Deck>> =
        withContext(Dispatchers.IO) {
            try {
                val decks = supabase.postgrest["decks"]
                    .select(Columns.ALL) {
                        order("updated_at", Order.DESCENDING)
                        limit(10)
                    }
                    .decodeList<DeckDto>()
                    .map { it.toDeck() }
                Result.Success(decks)
            } catch (e: Exception) {
                Result.Error("Could not load featured decks: ${e.message}", e)
            }
        }

    suspend fun getDeckById(deckId: Int): Result<Deck> =
        withContext(Dispatchers.IO) {
            try {
                val deck = supabase.postgrest["decks"]
                    .select(Columns.ALL) {
                        filter { eq("id", deckId) }
                    }
                    .decodeSingle<DeckDto>()
                    .toDeck()
                Result.Success(deck)
            } catch (e: Exception) {
                Result.Error("Deck not found: ${e.message}", e)
            }
        }

    suspend fun updateDeck(deckId: Int, name: String, format: DeckFormat): Result<Deck> =
        withContext(Dispatchers.IO) {
            try {
                val updated = supabase.postgrest["decks"]
                    .update(
                        buildJsonObject {
                            put("name", name)
                            put("format", format.value)
                        }
                    ) {
                        select()
                        filter { eq("id", deckId) }
                    }
                    .decodeSingle<DeckDto>()
                    .toDeck()
                Result.Success(updated)
            } catch (e: Exception) {
                Result.Error("Could not update deck: ${e.message}", e)
            }
        }

    suspend fun updateCoverImage(deckId: Int, imageUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["decks"]
                    .update(buildJsonObject { put("cover_image_url", imageUrl) }) {
                        filter { eq("id", deckId) }
                    }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error("Could not update cover: ${e.message}", e)
            }
        }

    suspend fun updateCommanderCardId(deckId: Int, cardId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["decks"]
                    .update(buildJsonObject { put("commander_card_id", cardId) }) {
                        filter { eq("id", deckId) }
                    }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error("Could not save commander: ${e.message}", e)
            }
        }

    suspend fun deleteDeck(deckId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["deck_cards"].delete {
                    filter { eq("deck_id", deckId) }
                }
                supabase.postgrest["deck_stats"].delete {
                    filter { eq("deck_id", deckId) }
                }
                supabase.postgrest["matches"].delete {
                    filter { eq("deck_id", deckId) }
                }
                supabase.postgrest["decks"].delete {
                    filter { eq("id", deckId) }
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error("Could not delete deck: ${e.message}", e)
            }
        }
}