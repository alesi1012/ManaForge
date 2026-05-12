package com.example.manaforge

import com.manaforge.api.ScryfallApi
import com.manaforge.api.ScryfallCardDto
import com.manaforge.data.mappers.toCard
import com.manaforge.data.models.Card
import com.manaforge.data.models.DeckCard
import com.manaforge.data.models.DeckCardWithDetails
import com.manaforge.data.models.Result
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(
    private val scryfallApi: ScryfallApi,
    private val supabase: SupabaseClient
) {

    // ── Scryfall search ────────────────────────────────────────────────────

    suspend fun searchScryfall(query: String): Result<List<ScryfallCardDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = scryfallApi.searchCards(query)
                Result.Success(response.data)
            } catch (e: Exception) {
                Result.Error("Scryfall search failed: ${e.message}", e)
            }
        }

    // ── Get or insert card in Supabase cache ───────────────────────────────

    suspend fun getOrInsertCard(dto: ScryfallCardDto): Result<Card> =
        withContext(Dispatchers.IO) {
            try {
                // Check if already cached by scryfallId
                val existing = supabase.postgrest["cards"]
                    .select(Columns.ALL) {
                        filter { eq("scryfall_id", dto.id) }
                        limit(1)
                    }
                    .decodeList<Card>()

                if (existing.isNotEmpty()) return@withContext Result.Success(existing.first())

                // Insert new card
                val card = dto.toCard()
                val inserted = supabase.postgrest["cards"]
                    .insert(card)
                    .decodeSingle<Card>()

                Result.Success(inserted)
            } catch (e: Exception) {
                Result.Error("Could not cache card: ${e.message}", e)
            }
        }

    // ── Cards in a deck ────────────────────────────────────────────────────

    suspend fun getDeckCards(deckId: Int): Result<List<DeckCardWithDetails>> =
        withContext(Dispatchers.IO) {
            try {
                // Fetch pivot rows
                val deckCards = supabase.postgrest["deck_cards"]
                    .select(Columns.ALL) {
                        filter { eq("deck_id", deckId) }
                    }
                    .decodeList<DeckCard>()

                // Fetch card details for each
                val withDetails = deckCards.mapNotNull { dc ->
                    val cardResult = getCardById(dc.cardId)
                    if (cardResult is Result.Success) {
                        DeckCardWithDetails(dc, cardResult.data)
                    } else null
                }
                Result.Success(withDetails)
            } catch (e: Exception) {
                Result.Error("Could not load deck cards: ${e.message}", e)
            }
        }

    // ── Add card to deck ───────────────────────────────────────────────────

    suspend fun addCardToDeck(
        deckId: Int,
        cardId: Int,
        quantity: Int = 1
    ): Result<DeckCard> = withContext(Dispatchers.IO) {
        try {
            // Check if card already in deck
            val existing = supabase.postgrest["deck_cards"]
                .select(Columns.ALL) {
                    filter {
                        eq("deck_id", deckId)
                        eq("card_id", cardId)
                    }
                    limit(1)
                }
                .decodeList<DeckCard>()

            if (existing.isNotEmpty()) {
                // Update quantity
                val newQty = existing.first().quantity + quantity
                return@withContext updateCardQuantity(existing.first().id, newQty)
            }

            val deckCard = DeckCard(deckId = deckId, cardId = cardId, quantity = quantity)
            val inserted = supabase.postgrest["deck_cards"]
                .insert(deckCard)
                .decodeSingle<DeckCard>()

            // Touch deck updated_at
            touchDeckTimestamp(deckId)

            Result.Success(inserted)
        } catch (e: Exception) {
            Result.Error("Could not add card: ${e.message}", e)
        }
    }

    // ── Update quantity ────────────────────────────────────────────────────

    suspend fun updateCardQuantity(deckCardId: Int, quantity: Int): Result<DeckCard> =
        withContext(Dispatchers.IO) {
            try {
                val updated = supabase.postgrest["deck_cards"]
                    .update(buildJsonObject { put("quantity", quantity) }) {
                        filter { eq("id", deckCardId) }
                    }
                    .decodeSingle<DeckCard>()
                Result.Success(updated)
            } catch (e: Exception) {
                Result.Error("Could not update quantity: ${e.message}", e)
            }
        }

    // ── Remove card from deck ──────────────────────────────────────────────

    suspend fun removeCardFromDeck(deckCardId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["deck_cards"].delete {
                    filter { eq("id", deckCardId) }
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error("Could not remove card: ${e.message}", e)
            }
        }

    // ── Private helpers ────────────────────────────────────────────────────

    private suspend fun getCardById(cardId: Int): Result<Card> =
        try {
            val card = supabase.postgrest["cards"]
                .select(Columns.ALL) {
                    filter { eq("id", cardId) }
                }
                .decodeSingle<Card>()
            Result.Success(card)
        } catch (e: Exception) {
            Result.Error("Card not found", e)
        }

    private suspend fun touchDeckTimestamp(deckId: Int) {
        try {
            supabase.postgrest["decks"]
                .update(buildJsonObject { put("updated_at", "now()") }) {
                    filter { eq("id", deckId) }
                }
        } catch (_: Exception) { /* non-critical */ }
    }
}