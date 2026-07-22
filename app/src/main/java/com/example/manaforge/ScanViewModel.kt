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

            when (val ocrResult = ocrProcessor.extractLines(imagePath)) {

                is Result.Error -> {

                    _scanState.value =
                        ScanState.Error(ocrResult.message)

                }

                is Result.Success -> {

                    val cardName =
                        ocrProcessor.extractCardName(ocrResult.data)

                    if (cardName.isNullOrBlank()) {

                        _scanState.value = ScanState.Error(
                            "No se ha podido reconocer la carta."
                        )

                        return@launch
                    }

                    // Intentamos buscar la carta con fuzzy search
                    val fuzzyResult =
                        cardRepository.getCardDtoByFuzzyName(cardName)

                    if (fuzzyResult is Result.Success) {

                        _scanState.value =
                            ScanState.Success(
                                cardName,
                                fuzzyResult.data
                            )

                        return@launch
                    }

                    // Si falla, hacemos una búsqueda normal
                    val searchResult =
                        cardRepository.searchScryfall(cardName)

                    val firstCard =
                        (searchResult as? Result.Success)
                            ?.data
                            ?.firstOrNull()

                    _scanState.value =
                        if (firstCard != null) {

                            ScanState.Success(
                                cardName,
                                firstCard
                            )

                        } else {

                            ScanState.NotFound(cardName)
                        }
                }

                Result.Loading -> {

                    _scanState.value =
                        ScanState.Processing

                }
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
