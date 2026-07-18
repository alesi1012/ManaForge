package com.example.manaforge

import android.util.Log
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

private const val TAG = "ManaForge"

@Singleton
class CollectionRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val cardRepository: CardRepository
) {

    suspend fun getCollectionCards(userId: Int): Result<List<CollectionCardWithDetails>> =
        withContext(Dispatchers.IO) {
            try {
                val items = supabase.postgrest["collection_cards"]
                    .select(Columns.ALL) {
                        filter { eq("user_id", userId) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<CollectionCard>()

                val withDetails = items.mapNotNull { cc ->
                    val cardResult = cardRepository.getCardById(cc.cardId)
                    if (cardResult is Result.Success) CollectionCardWithDetails(cc, cardResult.data)
                    else null
                }
                Result.Success(withDetails)
            } catch (e: Exception) {
                Log.e(TAG, "getCollectionCards failed", e)
                Result.Error("Could not load collection: ${e.message}", e)
            }
        }

    suspend fun addToCollection(
        userId: Int,
        cardId: Int,
        quantity: Int,
        foil: Boolean,
        condition: String
    ): Result<CollectionCard> = withContext(Dispatchers.IO) {
        try {
            val inserted = supabase.postgrest["collection_cards"]
                .insert(buildJsonObject {
                    put("user_id", userId)
                    put("card_id", cardId)
                    put("quantity", quantity)
                    put("foil", foil)
                    put("condition", condition)
                }) { select() }
                .decodeSingle<CollectionCard>()
            Result.Success(inserted)
        } catch (e: Exception) {
            Log.e(TAG, "addToCollection failed", e)
            Result.Error("Could not add to collection: ${e.message}", e)
        }
    }

    suspend fun removeFromCollection(collectionCardId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabase.postgrest["collection_cards"].delete {
                    filter { eq("id", collectionCardId) }
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "removeFromCollection failed", e)
                Result.Error("Could not remove from collection: ${e.message}", e)
            }
        }

    suspend fun updateCollectionCard(
        collectionCardId: Int,
        quantity: Int,
        foil: Boolean,
        condition: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["collection_cards"]
                .update(buildJsonObject {
                    put("quantity", quantity)
                    put("foil", foil)
                    put("condition", condition)
                }) {
                    filter { eq("id", collectionCardId) }
                }
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateCollectionCard failed", e)
            Result.Error("Could not update collection card: ${e.message}", e)
        }
    }
}
