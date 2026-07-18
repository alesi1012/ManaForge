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
class ScanViewModel @Inject constructor(
    private val ocrProcessor: OcrProcessor,
    private val cardRepository: CardRepository,
    private val collectionRepository: CollectionRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    sealed class ScanState {
        data object Processing : ScanState()
        data class Success(val ocrText: String, val card: ScryfallCardDto) : ScanState()
        data class NotFound(val ocrText: String) : ScanState()
        data class Error(val message: String) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Processing)
    val scanState: StateFlow<ScanState> = _scanState

    private val _addResult = MutableStateFlow<Result<Unit>?>(null)
    val addResult: StateFlow<Result<Unit>?> = _addResult

    fun processImage(imagePath: String) {
        viewModelScope.launch {
            _scanState.value = ScanState.Processing

            val ocrResult = ocrProcessor.extractText(imagePath)
            if (ocrResult is Result.Error) {
                _scanState.value = ScanState.Error(ocrResult.message)
                return@launch
            }

            val rawText = (ocrResult as Result.Success).data
            val cardName = extractCardName(rawText)

            if (cardName.isBlank()) {
                _scanState.value = ScanState.Error("No text detected. Ensure the card is well-lit and in focus.")
                return@launch
            }

            _scanState.value = ScanState.Processing

            // Try fuzzy match first (handles small OCR errors)
            val fuzzyResult = cardRepository.getCardDtoByFuzzyName(cardName)
            if (fuzzyResult is Result.Success) {
                _scanState.value = ScanState.Success(cardName, fuzzyResult.data)
                return@launch
            }

            // Fall back to broad search
            val searchResult = cardRepository.searchScryfall(cardName)
            val firstCard = (searchResult as? Result.Success)?.data?.firstOrNull()

            _scanState.value = if (firstCard != null) {
                ScanState.Success(cardName, firstCard)
            } else {
                ScanState.NotFound(cardName)
            }
        }
    }

    fun addToCollection(dto: ScryfallCardDto, quantity: Int, foil: Boolean, condition: String) {
        viewModelScope.launch {
            val cardResult = cardRepository.getOrInsertCard(dto)
            if (cardResult is Result.Error) {
                _addResult.value = cardResult
                return@launch
            }
            val card = (cardResult as Result.Success).data
            val userId = authRepository.currentIntUserIdOrRestore()
            val result = collectionRepository.addToCollection(userId, card.id, quantity, foil, condition)
            _addResult.value = when (result) {
                is Result.Success -> Result.Success(Unit)
                is Result.Error -> result
                else -> null
            }
        }
    }

    fun clearAddResult() {
        _addResult.value = null
    }

    private fun extractCardName(ocrText: String): String {
        val lines = ocrText.lines()
            .map { line ->
                line
                    .replace(Regex("\\{[^}]+\\}"), "") // strip mana symbols like {2}{W}
                    .replace(Regex("[^\\p{L}\\p{N}\\s',-]"), " ")
                    .trim()
            }
            .filter { it.length >= 3 }
            .filter { !it.matches(Regex("[\\d/]+")) } // skip P/T ratios like "3/3"

        return lines.firstOrNull() ?: ""
    }
}
