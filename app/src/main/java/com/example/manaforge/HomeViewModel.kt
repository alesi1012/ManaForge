package com.example.manaforge


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manaforge.Deck
import com.example.manaforge.Result
import com.example.manaforge.GetFeaturedDecksUseCase
import com.example.manaforge.GetUserDecksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getFeaturedDecks: GetFeaturedDecksUseCase,
    private val getUserDecks: GetUserDecksUseCase,
    private val logoutUser: LogoutUserUseCase
) : ViewModel() {

    private val _featuredDecks = MutableStateFlow<Result<List<Deck>>>(Result.Loading)
    val featuredDecks: StateFlow<Result<List<Deck>>> = _featuredDecks

    private val _userDecks = MutableStateFlow<Result<List<Deck>>>(Result.Loading)
    val userDecks: StateFlow<Result<List<Deck>>> = _userDecks

    private val _logoutState = MutableStateFlow<Result<Unit>?>(null)
    val logoutState: StateFlow<Result<Unit>?> = _logoutState

    fun loadFeaturedDecks() {
        viewModelScope.launch {
            _featuredDecks.value = Result.Loading
            _featuredDecks.value = getFeaturedDecks()
        }
    }

    fun loadUserDecks(userId: Int) {
        viewModelScope.launch {
            _userDecks.value = Result.Loading
            _userDecks.value = getUserDecks(userId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            _logoutState.value = Result.Loading
            _logoutState.value = logoutUser()
        }
    }
}