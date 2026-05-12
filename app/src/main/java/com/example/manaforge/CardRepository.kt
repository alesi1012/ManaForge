package com.example.manaforge

import com.example.manaforge.Api.ScryfallApi
import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.toCard
import com.example.manaforge.Card
import com.example.manaforge.DeckCard
import com.example.manaforge.DeckCardWithDetails
import com.example.manaforge.Result
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

    suspend fun searchScryfall(query: String): Result<List<ScryfallCardDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = scryfallApi.searchCards(query)
                Result.Success(response.data)
            } catch (e: Exception) {
                Result.Error("Scryfall search failed: ${e.message}", e)
            }
        }

    suspend fun getOrInsertCard(dto: ScryfallCardDto): Result<Card> =
        withContext(Dispatchers.IO) {
            try {
                val existing = supabase.postgrest["cards"]
                    .select(Columns.ALL) {
                        filter { eq("scryfall_id", dto.id) }
                    }
                    .decodeList<Card>()

                if (existing.isNotEmpty()) return@withContext Result.Success(existing.first())

                val card = dto.toCard()
                val inserted = supabase.postgrest["cards"]
                    .insert(card)
                    .decodeSingle<Card>()

                Result.Success(inserted)
            } catch (e: Exception) {
                Result.Error("Could not cache card: ${e.message}", e)
            }
        }

    suspend fun getDeckCards(deckId: Int): Result<List<DeckCardWithDetails>> =
        withContext(Dispatchers.IO) {
            try {
                val deckCards = supabase.postgrest["deck_cards"]
                    .select(Columns.ALL) {
                        filter { eq("deck_id", deckId) }
                    }
                    .decodeList<DeckCard>()

                val withDetails = deckCards.mapNotNull { dc ->
                    val cardResult = getCardById(dc.cardId)
                    if (cardResult is Result.Success) DeckCardWithDetails(dc, cardResult.data)
                    else null
                }
                Result.Success(withDetails)
            } catch (e: Exception) {
                Result.Error("Could not load deck cards: ${e.message}", e)
            }
        }

    suspend fun addCardToDeck(deckId: Int, cardId: Int, quantity: Int = 1): Result<DeckCard> =
        withContext(Dispatchers.IO) {
            try {
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
                    val newQty = existing.first().quantity + quantity
                    return@withContext updateCardQuantity(existing.first().id, newQty)
                }

                val deckCard = DeckCard(deckId = deckId, cardId = cardId, quantity = quantity)
                val inserted = supabase.postgrest["deck_cards"]
                    .insert(deckCard)
                    .decodeSingle<DeckCard>()

                touchDeckTimestamp(deckId)
                Result.Success(inserted)
            } catch (e: Exception) {
                Result.Error("Could not add card: ${e.message}", e)
            }
        }

    suspend fun updateCardQuantity(deckCardId: Int, quantity: Int): Result<DeckCard> =
        withContext(Dispatchers.IO) {
            try {
                val updated = supabase.postgrest["deck_cards"]
                    .update(buildJsonObject { put("quantity", quantity) }) {
                        select()
                        filter { eq("id", deckCardId) }
                    }
                    .decodeSingle<DeckCard>()
                Result.Success(updated)
            } catch (e: Exception) {
                Result.Error("Could not update quantity: ${e.message}", e)
            }
        }

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
        } catch (_: Exception) { }
    }
}
