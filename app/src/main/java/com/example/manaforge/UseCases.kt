package com.example.manaforge



import com.manaforge.api.ScryfallCardDto
import com.manaforge.data.models.*
import com.manaforge.data.repositories.*
import com.example.manaforge.DeckValidator
import com.example.manaforge.ValidationResult
import javax.inject.Inject

// ─────────────────────────────────────────────
//  Auth use cases
// ─────────────────────────────────────────────

class RegisterUserUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(username: String, email: String, password: String): Result<User> =
        repo.register(username, email, password)
}

class LoginUserUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<User> =
        repo.login(email, password)
}

class LogoutUserUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(): Result<Unit> = repo.logout()
}

// ─────────────────────────────────────────────
//  Deck use cases
// ─────────────────────────────────────────────

class CreateDeckUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(userId: Int, name: String, format: DeckFormat): Result<Deck> =
        repo.createDeck(userId, name, format)
}

class GetUserDecksUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(userId: Int): Result<List<Deck>> =
        repo.getDecksByUser(userId)
}

class GetFeaturedDecksUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(): Result<List<Deck>> = repo.getFeaturedDecks()
}

class UpdateDeckUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(deckId: Int, name: String, format: DeckFormat): Result<Deck> =
        repo.updateDeck(deckId, name, format)
}

class DeleteDeckUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(deckId: Int): Result<Unit> = repo.deleteDeck(deckId)
}

// ─────────────────────────────────────────────
//  Card use cases
// ─────────────────────────────────────────────

class SearchCardsUseCase @Inject constructor(private val repo: CardRepository) {
    suspend operator fun invoke(query: String): Result<List<ScryfallCardDto>> =
        repo.searchScryfall(query)
}

class AddCardToDeckUseCase @Inject constructor(
    private val cardRepo: CardRepository
) {
    suspend operator fun invoke(
        deckId: Int,
        dto: ScryfallCardDto,
        quantity: Int = 1
    ): Result<DeckCard> {
        // Ensure card is cached in Supabase
        val cardResult = cardRepo.getOrInsertCard(dto)
        if (cardResult is Result.Error) return cardResult

        val card = (cardResult as Result.Success).data
        return cardRepo.addCardToDeck(deckId, card.id, quantity)
    }
}

class RemoveCardFromDeckUseCase @Inject constructor(private val repo: CardRepository) {
    suspend operator fun invoke(deckCardId: Int): Result<Unit> =
        repo.removeCardFromDeck(deckCardId)
}

class UpdateCardQuantityUseCase @Inject constructor(private val repo: CardRepository) {
    suspend operator fun invoke(deckCardId: Int, quantity: Int): Result<DeckCard> =
        repo.updateCardQuantity(deckCardId, quantity)
}

class GetDeckCardsUseCase @Inject constructor(private val repo: CardRepository) {
    suspend operator fun invoke(deckId: Int): Result<List<DeckCardWithDetails>> =
        repo.getDeckCards(deckId)
}

// ─────────────────────────────────────────────
//  Validation use case
// ─────────────────────────────────────────────

class ValidateDeckUseCase @Inject constructor(private val cardRepo: CardRepository) {
    suspend operator fun invoke(deckId: Int, format: DeckFormat): ValidationResult {
        val cardsResult = cardRepo.getDeckCards(deckId)
        if (cardsResult is Result.Error) {
            return ValidationResult(isValid = false, errors = listOf(cardsResult.message))
        }
        val cards = (cardsResult as Result.Success).data
        return DeckValidator.validate(format, cards)
    }
}

// ─────────────────────────────────────────────
//  Stats / Match use cases
// ─────────────────────────────────────────────

class RecordMatchUseCase @Inject constructor(private val repo: MatchRepository) {
    suspend operator fun invoke(match: Match): Result<Match> = repo.recordMatch(match)
}

class GetMatchesUseCase @Inject constructor(private val repo: MatchRepository) {
    suspend operator fun invoke(deckId: Int): Result<List<Match>> =
        repo.getMatchesForDeck(deckId)
}

class GetDeckStatsUseCase @Inject constructor(private val repo: StatsRepository) {
    suspend operator fun invoke(deckId: Int): Result<DeckStats> = repo.getStats(deckId)
}