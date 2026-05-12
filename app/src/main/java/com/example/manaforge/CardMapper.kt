package com.example.manaforge

import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.Api.resolveArtCropUrl
import com.example.manaforge.Api.resolveImageUrl
import com.example.manaforge.Card

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