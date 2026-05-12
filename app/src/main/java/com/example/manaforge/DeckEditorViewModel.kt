package com.example.manaforge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.Api.resolveImageUrl
import com.example.manaforge.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeckEditorViewModel @Inject constructor(
    private val createDeck: CreateDeckUseCase,
    private val updateDeck: UpdateDeckUseCase,
    private val deleteDeck: DeleteDeckUseCase,
    private val getDeckCards: GetDeckCardsUseCase,
    private val searchCards: SearchCardsUseCase,
    private val addCardToDeck: AddCardToDeckUseCase,
    private val removeCardUseCase: RemoveCardFromDeckUseCase,
    private val updateQuantityUseCase: UpdateCardQuantityUseCase,
    private val validateDeck: ValidateDeckUseCase
) : ViewModel() {

    private val _deck = MutableStateFlow<Deck?>(null)
    val deck: StateFlow<Deck?> = _deck

    private val _deckCards = MutableStateFlow<Result<List<DeckCardWithDetails>>>(Result.Loading)
    val deckCards: StateFlow<Result<List<DeckCardWithDetails>>> = _deckCards

    private val _searchResults = MutableStateFlow<Result<List<ScryfallCardDto>>>(Result.Success(emptyList()))
    val searchResults: StateFlow<Result<List<ScryfallCardDto>>> = _searchResults

    private val _validationResult = MutableStateFlow<ValidationResult?>(null)
    val validationResult: StateFlow<ValidationResult?> = _validationResult

    private val _operationState = MutableStateFlow<Result<Unit>?>(null)
    val operationState: StateFlow<Result<Unit>?> = _operationState

    private val _commander = MutableStateFlow<Card?>(null)
    val commander: StateFlow<Card?> = _commander

    private val _isPickingCommander = MutableStateFlow(false)
    val isPickingCommander: StateFlow<Boolean> = _isPickingCommander

    // ── Deck ops ──────────────────────────────────────────────────────────

    fun loadDeck(deck: Deck) {
        _deck.value = deck
        loadDeckCards(deck.id)
    }

    fun editDeck(deckId: Int, name: String, format: DeckFormat) {
        viewModelScope.launch {
            _operationState.value = Result.Loading
            when (val r = updateDeck(deckId, name, format)) {
                is Result.Success -> {
                    _deck.value = r.data
                    _operationState.value = Result.Success(Unit)
                }
                is Result.Error -> _operationState.value = Result.Error(r.message)
                else -> Unit
            }
        }
    }

    fun removeDeck(deckId: Int) {
        viewModelScope.launch {
            _operationState.value = Result.Loading
            _operationState.value = when (val r = deleteDeck(deckId)) {
                is Result.Success -> Result.Success(Unit)
                is Result.Error -> Result.Error(r.message)
                else -> Result.Loading
            }
        }
    }

    // ── Card ops ──────────────────────────────────────────────────────────

    private fun loadDeckCards(deckId: Int) {
        viewModelScope.launch {
            _deckCards.value = Result.Loading
            val result = getDeckCards(deckId)
            _deckCards.value = result
            if (result is Result.Success) {
                val commanderEntry = result.data.firstOrNull { it.deckCard.isCommander }
                if (commanderEntry != null) _commander.value = commanderEntry.card
            }
        }
    }

    fun searchScryfall(query: String, commanderMode: Boolean = false) {
        if (query.length < 2) return
        viewModelScope.launch {
            _searchResults.value = Result.Loading
            val q = if (commanderMode) "is:commander $query" else query
            _searchResults.value = searchCards(q)
        }
    }

    fun addCard(dto: ScryfallCardDto, quantity: Int = 1, isCommander: Boolean = false) {
        val deckId = _deck.value?.id ?: return
        viewModelScope.launch {
            val r = addCardToDeck(deckId, dto, quantity, isCommander)
            if (r is Result.Error) _operationState.value = Result.Error(r.message)
            loadDeckCards(deckId)
        }
    }

    fun removeCard(deckCardId: Int) {
        val deckId = _deck.value?.id ?: return
        viewModelScope.launch {
            removeCardUseCase(deckCardId)
            loadDeckCards(deckId)
        }
    }

    fun updateQuantity(deckCardId: Int, quantity: Int) {
        val deckId = _deck.value?.id ?: return
        viewModelScope.launch {
            updateQuantityUseCase(deckCardId, quantity)
            loadDeckCards(deckId)
        }
    }

    // ── Commander ops ─────────────────────────────────────────────────────

    fun startPickingCommander() {
        _isPickingCommander.value = true
    }

    fun cancelPickingCommander() {
        _isPickingCommander.value = false
    }

    fun setCommander(dto: ScryfallCardDto) {
        _commander.value = Card(
            name = dto.name,
            type = dto.typeLine,
            manaCost = dto.manaCost,
            imageUrl = dto.resolveImageUrl()
        )
        _isPickingCommander.value = false
        addCard(dto, 1, isCommander = true)
    }

    // ── Validation ────────────────────────────────────────────────────────

    fun validateCurrentDeck() {
        val deck = _deck.value ?: return
        viewModelScope.launch {
            _validationResult.value = validateDeck(deck.id, deck.format)
        }
    }
}
