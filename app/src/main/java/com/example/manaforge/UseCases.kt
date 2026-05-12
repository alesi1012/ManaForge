package com.example.manaforge



import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.*
import com.example.manaforge.DeckValidator
import com.example.manaforge.ValidationResult
import javax.inject.Inject



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

class UpdateDeckCoverImageUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(deckId: Int, imageUrl: String): Result<Unit> =
        repo.updateCoverImage(deckId, imageUrl)
}

class UpdateCommanderCardIdUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(deckId: Int, cardId: Int): Result<Unit> =
        repo.updateCommanderCardId(deckId, cardId)
}

class GetCardByIdUseCase @Inject constructor(private val repo: CardRepository) {
    suspend operator fun invoke(cardId: Int): Result<Card> =
        repo.getCardById(cardId)
}

class GetDeckByIdUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(deckId: Int): Result<Deck> =
        repo.getDeckById(deckId)
}

class DeleteDeckUseCase @Inject constructor(private val repo: DeckRepository) {
    suspend operator fun invoke(deckId: Int): Result<Unit> = repo.deleteDeck(deckId)
}



class SearchCardsUseCase @Inject constructor(private val repo: CardRepository) {
    suspend operator fun invoke(query: String): Result<List<ScryfallCardDto>> =
        repo.searchScryfall(query)
}

class GetCardDtoByNameUseCase @Inject constructor(private val repo: CardRepository) {
    suspend operator fun invoke(name: String): Result<ScryfallCardDto> =
        repo.getCardDtoByExactName(name)
}

class AddCardToDeckUseCase @Inject constructor(
    private val cardRepo: CardRepository
) {
    suspend operator fun invoke(
        deckId: Int,
        dto: ScryfallCardDto,
        quantity: Int = 1,
        isCommander: Boolean = false
    ): Result<DeckCard> {
        val cardResult = cardRepo.getOrInsertCard(dto)
        if (cardResult is Result.Error) return cardResult
        val card = (cardResult as Result.Success).data
        return cardRepo.addCardToDeck(deckId, card.id, quantity, isCommander)
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