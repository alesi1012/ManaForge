package com.example.manaforge

import com.manaforge.api.ScryfallCardDto
import com.manaforge.api.resolveArtCropUrl
import com.manaforge.api.resolveImageUrl
import com.manaforge.data.models.Card

/**
 * Maps a Scryfall API DTO to the internal [Card] domain model.
 * The resulting card has id = 0 (will be assigned by Supabase on insert).
 */
fun ScryfallCardDto.toCard(): Card = Card(
    id = 0,
    name = name,
    type = typeLine,
    manaCost = manaCost,
    colors = colors?.joinToString(","),
    text = oracleText,
    power = power,
    toughness = toughness,
    rarity = rarity,
    setName = setName,
    imageUrl = resolveImageUrl(),
    artCropUrl = resolveArtCropUrl(),
    scryfallId = id
)