package com.example.manaforge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────
//  User
// ─────────────────────────────────────────────

@Serializable
data class User(
    val id: Int = 0,
    val username: String,
    val email: String,
    @SerialName("password_hash") val passwordHash: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

// ─────────────────────────────────────────────
//  Deck
// ─────────────────────────────────────────────

@Serializable
data class Deck(
    val id: Int = 0,
    @SerialName("user_id") val userId: Int,
    val name: String,
    val format: DeckFormat,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
enum class DeckFormat(val value: String) {
    @SerialName("standard") STANDARD("standard"),
    @SerialName("commander") COMMANDER("commander");

    companion object {
        fun from(value: String): DeckFormat =
            entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

// ─────────────────────────────────────────────
//  Card  (Supabase cache)
// ─────────────────────────────────────────────

@Serializable
data class Card(
    val id: Int = 0,
    val name: String,
    val type: String?,
    @SerialName("mana_cost") val manaCost: String?,
    val colors: String?,
    val text: String?,
    val power: String?,
    val toughness: String?,
    val rarity: String?,
    @SerialName("set_name") val setName: String?,
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("art_crop_url") val artCropUrl: String?,
    @SerialName("scryfall_id") val scryfallId: String?
)

// ─────────────────────────────────────────────
//  DeckCard  (pivot)
// ─────────────────────────────────────────────

@Serializable
data class DeckCard(
    val id: Int = 0,
    @SerialName("deck_id") val deckId: Int,
    @SerialName("card_id") val cardId: Int,
    val quantity: Int = 1
)

/**
 * Aggregated view used in the UI: card data + quantity
 */
data class DeckCardWithDetails(
    val deckCard: DeckCard,
    val card: Card
) {
    val quantity: Int get() = deckCard.quantity
}

// ─────────────────────────────────────────────
//  DeckStats
// ─────────────────────────────────────────────

@Serializable
data class DeckStats(
    @SerialName("deck_id") val deckId: Int,
    @SerialName("total_matches") val totalMatches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    @SerialName("total_damage_dealt") val totalDamageDealt: Int = 0,
    @SerialName("total_damage_taken") val totalDamageTaken: Int = 0
) {
    val winRate: Float
        get() = if (totalMatches > 0) wins.toFloat() / totalMatches else 0f
}

// ─────────────────────────────────────────────
//  Match
// ─────────────────────────────────────────────

@Serializable
data class Match(
    val id: Int = 0,
    @SerialName("deck_id") val deckId: Int,
    @SerialName("opponent_name") val opponentName: String,
    val result: MatchResult,
    @SerialName("damage_dealt") val damageDealt: Int = 0,
    @SerialName("damage_taken") val damageTaken: Int = 0,
    @SerialName("turns_played") val turnsPlayed: Int = 0,
    @SerialName("date_played") val datePlayed: String = ""
)

@Serializable
enum class MatchResult(val value: String) {
    @SerialName("win") WIN("win"),
    @SerialName("loss") LOSS("loss"),
    @SerialName("draw") DRAW("draw");

    companion object {
        fun from(value: String): MatchResult =
            entries.firstOrNull { it.value == value } ?: LOSS
    }
}

// ─────────────────────────────────────────────
//  UI state wrapper
// ─────────────────────────────────────────────

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}