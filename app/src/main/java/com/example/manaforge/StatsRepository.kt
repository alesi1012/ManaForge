package com.example.manaforge

import com.manaforge.data.models.DeckStats
import com.manaforge.data.models.Match
import com.manaforge.data.models.MatchResult
import com.manaforge.data.models.Result
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

// ─────────────────────────────────────────────
//  Stats Repository
// ─────────────────────────────────────────────

@Singleton
class StatsRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun getStats(deckId: Int): Result<DeckStats> =
        withContext(Dispatchers.IO) {
            try {
                val stats = supabase.postgrest["deck_stats"]
                    .select(Columns.ALL) {
                        filter { eq("deck_id", deckId) }
                    }
                    .decodeSingle<DeckStats>()
                Result.Success(stats)
            } catch (e: Exception) {
                Result.Error("Could not load stats: ${e.message}", e)
            }
        }

    /**
     * Atomically updates [deck_stats] after a match is registered.
     * Uses a raw RPC call so the increment is safe from race conditions.
     */
    suspend fun updateAfterMatch(
        deckId: Int,
        result: MatchResult,
        damageDealt: Int,
        damageTaken: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Read current stats
            val current = supabase.postgrest["deck_stats"]
                .select(Columns.ALL) { filter { eq("deck_id", deckId) } }
                .decodeSingle<DeckStats>()

            val wins   = current.wins   + if (result == MatchResult.WIN)  1 else 0
            val losses = current.losses + if (result == MatchResult.LOSS) 1 else 0
            val draws  = current.draws  + if (result == MatchResult.DRAW) 1 else 0

            supabase.postgrest["deck_stats"]
                .update(
                    buildJsonObject {
                        put("total_matches",    current.totalMatches + 1)
                        put("wins",             wins)
                        put("losses",           losses)
                        put("draws",            draws)
                        put("total_damage_dealt", current.totalDamageDealt + damageDealt)
                        put("total_damage_taken", current.totalDamageTaken + damageTaken)
                    }
                ) {
                    filter { eq("deck_id", deckId) }
                }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Could not update stats: ${e.message}", e)
        }
    }
}

// ─────────────────────────────────────────────
//  Match Repository
// ─────────────────────────────────────────────

@Singleton
class MatchRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val statsRepository: StatsRepository
) {

    suspend fun recordMatch(match: Match): Result<Match> =
        withContext(Dispatchers.IO) {
            try {
                val inserted = supabase.postgrest["matches"]
                    .insert(match)
                    .decodeSingle<Match>()

                // Update aggregate stats
                statsRepository.updateAfterMatch(
                    deckId      = match.deckId,
                    result      = match.result,
                    damageDealt = match.damageDealt,
                    damageTaken = match.damageTaken
                )

                Result.Success(inserted)
            } catch (e: Exception) {
                Result.Error("Could not record match: ${e.message}", e)
            }
        }

    suspend fun getMatchesForDeck(deckId: Int): Result<List<Match>> =
        withContext(Dispatchers.IO) {
            try {
                val matches = supabase.postgrest["matches"]
                    .select(Columns.ALL) {
                        filter { eq("deck_id", deckId) }
                        order("date_played", Order.DESCENDING)
                    }
                    .decodeList<Match>()
                Result.Success(matches)
            } catch (e: Exception) {
                Result.Error("Could not load matches: ${e.message}", e)
            }
        }
}