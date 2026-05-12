package com.example.manaforge

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// ─────────────────────────────────────────────
//  User
// ─────────────────────────────────────────────

@Serializable
data class User(
    val id: Int = 0,
    val username: String = "",
    val email: String = "",
    @SerialName("password_hash") val passwordHash: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

// ─────────────────────────────────────────────
//  Deck
// ─────────────────────────────────────────────

@Parcelize
data class Deck(
    val id: Int = 0,
    val userId: Int = 0,
    val name: String = "",
    val format: DeckFormat = DeckFormat.STANDARD,
    val createdAt: String = "",
    val updatedAt: String = ""
) : Parcelable

@Serializable
data class DeckDto(
    val id: Int = 0,
    @SerialName("user_id") val userId: Int = 0,
    val name: String = "",
    val format: String = "standard",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
) {
    fun toDeck() = Deck(
        id = id,
        userId = userId,
        name = name,
        format = DeckFormat.from(format),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Deck.toDto() = DeckDto(
    id = id,
    userId = userId,
    name = name,
    format = format.value,
    createdAt = createdAt,
    updatedAt = updatedAt
)

@Parcelize
enum class DeckFormat(val value: String) : Parcelable {
    STANDARD("standard"),
    COMMANDER("commander");

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
    val name: String = "",
    val type: String? = null,
    @SerialName("mana_cost") val manaCost: String? = null,
    val colors: String? = null,
    val text: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val rarity: String? = null,
    @SerialName("set_name") val setName: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("art_crop_url") val artCropUrl: String? = null,
    @SerialName("scryfall_id") val scryfallId: String? = null
)

// ─────────────────────────────────────────────
//  DeckCard  (pivot)
// ─────────────────────────────────────────────

@Serializable
data class DeckCard(
    val id: Int = 0,
    @SerialName("deck_id") val deckId: Int = 0,
    @SerialName("card_id") val cardId: Int = 0,
    val quantity: Int = 1
)

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
    @SerialName("deck_id") val deckId: Int = 0,
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
    @SerialName("deck_id") val deckId: Int = 0,
    @SerialName("opponent_name") val opponentName: String = "",
    val result: String = "loss",
    @SerialName("damage_dealt") val damageDealt: Int = 0,
    @SerialName("damage_taken") val damageTaken: Int = 0,
    @SerialName("turns_played") val turnsPlayed: Int = 0,
    @SerialName("date_played") val datePlayed: String = ""
) {
    fun matchResult() = GameResult.from(result)
}

enum class GameResult(val value: String) {
    WIN("win"),
    LOSS("loss"),
    DRAW("draw");

    companion object {
        fun from(value: String): GameResult =
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