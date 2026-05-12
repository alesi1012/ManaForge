package com.example.manaforge

import com.example.manaforge.DeckStats
import com.example.manaforge.Match
import com.example.manaforge.GameResult
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

    suspend fun updateAfterMatch(
        deckId: Int,
        result: GameResult,
        damageDealt: Int,
        damageTaken: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = supabase.postgrest["deck_stats"]
                .select(Columns.ALL) { filter { eq("deck_id", deckId) } }
                .decodeSingle<DeckStats>()

            val wins   = current.wins   + if (result == GameResult.WIN)  1 else 0
            val losses = current.losses + if (result == GameResult.LOSS) 1 else 0
            val draws  = current.draws  + if (result == GameResult.DRAW) 1 else 0

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

                statsRepository.updateAfterMatch(
                    deckId      = match.deckId,
                    result      = GameResult.from(match.result),
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