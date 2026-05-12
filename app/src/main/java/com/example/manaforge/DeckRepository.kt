package com.example.manaforge


import com.example.manaforge.Deck
import com.example.manaforge.DeckFormat
import com.example.manaforge.Result
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeckRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    // ── Create ────────────────────────────────────────────────────────────

    suspend fun createDeck(
        userId: Int,
        name: String,
        format: DeckFormat
    ): Result<Deck> = withContext(Dispatchers.IO) {
        try {
            val deck = Deck(userId = userId, name = name, format = format)
            val created = supabase.postgrest["decks"]
                .insert(deck)
                .decodeSingle<Deck>()

            // Initialise deck_stats row
            supabase.postgrest["deck_stats"].insert(
                buildJsonObject { put("deck_id", created.id) }
            )

            Result.Success(created)
        } catch (e: Exception) {
            Result.Error("Could not create deck: ${e.message}", e)
        }
    }

    // ── Read – all decks for a user ────────────────────────────────────────

    suspend fun getDecksByUser(userId: Int): Result<List<Deck>> =
        withContext(Dispatchers.IO) {
            try {
                val decks = supabase.postgrest["decks"]
                    .select(Columns.ALL) {
                        filter { eq("user_id", userId) }
                        order("updated_at", Order.DESCENDING)
                    }
                    .decodeList<Deck>()
                Result.Success(decks)
            } catch (e: Exception) {
                Result.Error("Could not load decks: ${e.message}", e)
            }
        }

    // ── Read – featured decks for carousel (all users, top 10) ────────────

    suspend fun getFeaturedDecks(): Result<List<Deck>> =
        withContext(Dispatchers.IO) {
            try {
                val decks = supabase.postgrest["decks"]
                    .select(Columns.ALL) {
                        order("updated_at", Order.DESCENDING)
                        limit(10)
                    }
                    .decodeList<Deck>()
                Result.Success(decks)
            } catch (e: Exception) {
                Result.Error("Could not load featured decks: ${e.message}", e)
            }
        }

    // ── Read – single deck ────────────────────────────────────────────────

    suspend fun getDeckById(deckId: Int): Result<Deck> =
        withContext(Dispatchers.IO) {
            try {
                val deck = supabase.postgrest["decks"]
                    .select(Columns.ALL) {
                        filter { eq("id", deckId) }
                    }
                    .decodeSingle<Deck>()
                Result.Success(deck)
            } catch (e: Exception) {
                Result.Error("Deck not found: ${e.message}", e)
            }
        }

    // ── Update ────────────────────────────────────────────────────────────

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
                        filter { eq("id", deckId) }
                    }
                    .decodeSingle<Deck>()
                Result.Success(updated)
            } catch (e: Exception) {
                Result.Error("Could not update deck: ${e.message}", e)
            }
        }

    // ── Delete ────────────────────────────────────────────────────────────

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