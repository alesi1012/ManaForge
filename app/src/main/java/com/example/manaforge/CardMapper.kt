package com.example.manaforge

import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.Api.resolveArtCropUrl
import com.example.manaforge.Api.resolveImageUrl
import com.example.manaforge.Card

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