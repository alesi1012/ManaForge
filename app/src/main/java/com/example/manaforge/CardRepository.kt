package com.example.manaforge

import com.example.manaforge.Api.ScryfallApi
import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.Api.resolveArtCropUrl
import com.example.manaforge.Api.resolveImageUrl
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

    suspend fun getCardDtoByExactName(name: String): Result<ScryfallCardDto> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(scryfallApi.getCardByName(name))
            } catch (e: Exception) {
                Result.Error("Not found: $name", e)
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

                if (existing.isNotEmpty()) {
                    val card = existing.first()
                    val resolvedUrl = dto.resolveImageUrl()
                    if (card.imageUrl == null && resolvedUrl != null) {
                        try {
                            supabase.postgrest["cards"]
                                .update(buildJsonObject { put("image_url", resolvedUrl) }) {
                                    filter { eq("id", card.id) }
                                }
                        } catch (_: Exception) { }
                        return@withContext Result.Success(card.copy(imageUrl = resolvedUrl))
                    }
                    return@withContext Result.Success(card)
                }

                val inserted = supabase.postgrest["cards"]
                    .insert(buildJsonObject {
                        put("name", dto.name)
                        dto.typeLine?.let { put("type", it) }
                        dto.manaCost?.let { put("mana_cost", it) }
                        dto.colors?.joinToString(",")?.let { put("colors", it) }
                        dto.oracleText?.let { put("text", it) }
                        dto.power?.let { put("power", it) }
                        dto.toughness?.let { put("toughness", it) }
                        dto.rarity?.let { put("rarity", it) }
                        dto.setName?.let { put("set_name", it) }
                        dto.resolveImageUrl()?.let { put("image_url", it) }
                        dto.resolveArtCropUrl()?.let { put("art_crop_url", it) }
                        put("scryfall_id", dto.id)
                    }) {
                        select()
                    }
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

    suspend fun addCardToDeck(
        deckId: Int,
        cardId: Int,
        quantity: Int = 1,
        isCommander: Boolean = false
    ): Result<DeckCard> =
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

                val result: DeckCard = if (existing.isNotEmpty()) {
                    val dc = existing.first()
                    val newQty = dc.quantity + quantity
                    supabase.postgrest["deck_cards"]
                        .update(buildJsonObject {
                            put("quantity", newQty)
                            if (isCommander) put("is_commander", true)
                        }) {
                            filter { eq("id", dc.id) }
                        }
                    dc.copy(
                        quantity = newQty,
                        isCommander = if (isCommander) true else dc.isCommander
                    )
                } else {
                    val inserted = supabase.postgrest["deck_cards"]
                        .insert(buildJsonObject {
                            put("deck_id", deckId)
                            put("card_id", cardId)
                            put("quantity", quantity)
                            put("is_commander", isCommander)
                        }) { select() }
                        .decodeSingle<DeckCard>()
                    touchDeckTimestamp(deckId)
                    inserted
                }

                Result.Success(result)
            } catch (e: Exception) {
                Result.Error("Could not add card: ${e.message}", e)
            }
        }

    suspend fun updateCardQuantity(deckCardId: Int, quantity: Int): Result<DeckCard> =
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["deck_cards"]
                    .update(buildJsonObject { put("quantity", quantity) }) {
                        filter { eq("id", deckCardId) }
                    }
                Result.Success(DeckCard(id = deckCardId, quantity = quantity))
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

    suspend fun getCardById(cardId: Int): Result<Card> =
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
            val now = java.time.Instant.now().toString()
            supabase.postgrest["decks"]
                .update(buildJsonObject { put("updated_at", now) }) {
                    filter { eq("id", deckId) }
                }
        } catch (_: Exception) { }
    }
}
