package com.example.manaforge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manaforge.Api.ScryfallCardDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val cardRepository: CardRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _collection = MutableStateFlow<Result<List<CollectionCardWithDetails>>>(Result.Loading)
    val collection: StateFlow<Result<List<CollectionCardWithDetails>>> = _collection

    private val _searchResults = MutableStateFlow<Result<List<ScryfallCardDto>>>(Result.Success(emptyList()))
    val searchResults: StateFlow<Result<List<ScryfallCardDto>>> = _searchResults

    private val _operationResult = MutableStateFlow<Result<Unit>?>(null)
    val operationResult: StateFlow<Result<Unit>?> = _operationResult

    fun loadCollection() {
        viewModelScope.launch {
            _collection.value = Result.Loading
            val userId = authRepository.currentIntUserIdOrRestore()
            _collection.value = collectionRepository.getCollectionCards(userId)
        }
    }

    fun searchCards(query: String) {
        if (query.length < 3) {
            _searchResults.value = Result.Success(emptyList())
            return
        }
        viewModelScope.launch {
            _searchResults.value = Result.Loading
            _searchResults.value = cardRepository.searchScryfall(query)
        }
    }

    fun addToCollection(dto: ScryfallCardDto, quantity: Int, foil: Boolean, condition: String) {
        viewModelScope.launch {
            val cardResult = cardRepository.getOrInsertCard(dto)
            if (cardResult is Result.Error) {
                _operationResult.value = cardResult
                return@launch
            }
            val card = (cardResult as Result.Success).data
            val userId = authRepository.currentIntUserIdOrRestore()
            val result = collectionRepository.addToCollection(userId, card.id, quantity, foil, condition)
            _operationResult.value = when (result) {
                is Result.Success -> Result.Success(Unit)
                is Result.Error -> result
                else -> null
            }
            if (result is Result.Success) loadCollection()
        }
    }

    fun removeFromCollection(collectionCardId: Int) {
        viewModelScope.launch {
            val result = collectionRepository.removeFromCollection(collectionCardId)
            _operationResult.value = result
            if (result is Result.Success) loadCollection()
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }
}
