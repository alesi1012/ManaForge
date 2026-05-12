package com.example.manaforge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.*
import com.example.manaforge.*
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
    private val removeCard: RemoveCardFromDeckUseCase,
    private val updateQuantity: UpdateCardQuantityUseCase,
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

    // ── Deck ops ──────────────────────────────────────────────────────────

    fun createNewDeck(userId: Int, name: String, format: DeckFormat) {
        viewModelScope.launch {
            _operationState.value = Result.Loading
            when (val r = createDeck(userId, name, format)) {
                is Result.Success -> {
                    _deck.value = r.data
                    _operationState.value = Result.Success(Unit)
                    loadDeckCards(r.data.id)
                }
                is Result.Error -> _operationState.value = Result.Error(r.message)
                else -> Unit
            }
        }
    }

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
            _deckCards.value = getDeckCards(deckId)
        }
    }

    fun searchScryfall(query: String) {
        if (query.length < 2) return
        viewModelScope.launch {
            _searchResults.value = Result.Loading
            _searchResults.value = searchCards(query)
        }
    }

    fun addCard(dto: ScryfallCardDto, quantity: Int = 1) {
        val deckId = _deck.value?.id ?: return
        viewModelScope.launch {
            when (val r = addCardToDeck(deckId, dto, quantity)) {
                is Result.Success -> loadDeckCards(deckId)
                is Result.Error -> _operationState.value = Result.Error(r.message)
                else -> Unit
            }
        }
    }

    fun removeCard(deckCardId: Int) {
        val deckId = _deck.value?.id ?: return
        viewModelScope.launch {
            removeCard(deckCardId)
            loadDeckCards(deckId)
        }
    }

    fun updateQuantity(deckCardId: Int, quantity: Int) {
        val deckId = _deck.value?.id ?: return
        viewModelScope.launch {
            updateQuantity(deckCardId, quantity)
            loadDeckCards(deckId)
        }
    }

    // ── Validation ────────────────────────────────────────────────────────

    fun validateCurrentDeck() {
        val deck = _deck.value ?: return
        viewModelScope.launch {
            _validationResult.value = validateDeck(deck.id, deck.format)
        }
    }
}