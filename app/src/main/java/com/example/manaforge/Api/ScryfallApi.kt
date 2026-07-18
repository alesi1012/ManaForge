package com.example.manaforge.Api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query


interface ScryfallApi {

    @Headers(
        "User-Agent: ManaForge/1.0",
        "Accept: application/json"
    )
    @GET("cards/search")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("page") page: Int = 1
    ): ScryfallSearchResponse

    @Headers(
        "User-Agent: ManaForge/1.0",
        "Accept: application/json"
    )
    @GET("cards/named")
    suspend fun getCardByName(
        @Query("exact") name: String
    ): ScryfallCardDto

    @Headers(
        "User-Agent: ManaForge/1.0",
        "Accept: application/json"
    )
    @GET("cards/named")
    suspend fun getCardByFuzzyName(
        @Query("fuzzy") name: String
    ): ScryfallCardDto

    @Headers(
        "User-Agent: ManaForge/1.0",
        "Accept: application/json"
    )
    @GET("cards/search")
    suspend fun searchCommanders(
        @Query("q") query: String = "is:commander",
        @Query("page") page: Int = 1
    ): ScryfallSearchResponse
}



data class ScryfallSearchResponse(
    @SerializedName("data") val data: List<ScryfallCardDto>,
    @SerializedName("has_more") val hasMore: Boolean = false,
    @SerializedName("total_cards") val totalCards: Int = 0
)

data class ScryfallCardDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("mana_cost") val manaCost: String?,
    @SerializedName("type_line") val typeLine: String?,
    @SerializedName("oracle_text") val oracleText: String?,
    @SerializedName("colors") val colors: List<String>?,
    @SerializedName("power") val power: String?,
    @SerializedName("toughness") val toughness: String?,
    @SerializedName("rarity") val rarity: String?,
    @SerializedName("set_name") val setName: String?,
    @SerializedName("image_uris") val imageUris: ImageUrisDto?,
    @SerializedName("card_faces") val cardFaces: List<CardFaceDto>?,
    @SerializedName("legalities") val legalities: LegalitiesDto?,
    @SerializedName("prices") val prices: PricesDto?
)

data class PricesDto(
    @SerializedName("usd") val usd: String?,
    @SerializedName("usd_foil") val usdFoil: String?
)

data class ImageUrisDto(
    @SerializedName("small") val small: String?,
    @SerializedName("normal") val normal: String?,
    @SerializedName("large") val large: String?,
    @SerializedName("art_crop") val artCrop: String?
)

data class CardFaceDto(
    @SerializedName("name") val name: String?,
    @SerializedName("mana_cost") val manaCost: String?,
    @SerializedName("type_line") val typeLine: String?,
    @SerializedName("oracle_text") val oracleText: String?,
    @SerializedName("image_uris") val imageUris: ImageUrisDto?
)

data class LegalitiesDto(
    @SerializedName("standard") val standard: String?,
    @SerializedName("commander") val commander: String?
)



fun ScryfallCardDto.resolveImageUrl(): String? =
    imageUris?.normal
        ?: cardFaces?.firstOrNull()?.imageUris?.normal

fun ScryfallCardDto.resolveArtCropUrl(): String? =
    imageUris?.artCrop
        ?: cardFaces?.firstOrNull()?.imageUris?.artCrop

fun ScryfallCardDto.isLegalInStandard(): Boolean =
    legalities?.standard == "legal"

fun ScryfallCardDto.isLegalInCommander(): Boolean =
    legalities?.commander == "legal"