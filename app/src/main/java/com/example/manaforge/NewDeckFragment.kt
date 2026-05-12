package com.example.manaforge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.example.manaforge.databinding.FragmentNewDeckBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewDeckViewModel @Inject constructor(
    private val getUserDecks: GetUserDecksUseCase,
    private val createDeck: CreateDeckUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _decks = MutableStateFlow<Result<List<Deck>>>(Result.Loading)
    val decks: StateFlow<Result<List<Deck>>> = _decks

    private val _createState = MutableStateFlow<Result<Deck>?>(null)
    val createState: StateFlow<Result<Deck>?> = _createState

    fun loadDecks() {
        viewModelScope.launch {
            _decks.value = Result.Loading
            val userId = authRepository.currentIntUserIdOrRestore()
            _decks.value = getUserDecks(userId)
        }
    }

    fun create(name: String, format: DeckFormat) {
        viewModelScope.launch {
            _createState.value = Result.Loading
            val userId = authRepository.currentIntUserIdOrRestore()
            _createState.value = createDeck(userId, name, format)
        }
    }

    fun resetCreateState() {
        _createState.value = null
    }
}

@AndroidEntryPoint
class NewDeckFragment : Fragment() {

    private var _binding: FragmentNewDeckBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NewDeckViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewDeckBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = DeckListAdapter { deck ->
            val action = NewDeckFragmentDirections.actionNewDeckToEditor(deck)
            findNavController().navigate(action)
        }
        binding.recyclerDecks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        binding.fabCreateDeck.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.decks.collect { result ->
                when (result) {
                    is Result.Loading -> {
                        binding.progressDecks.isVisible = true
                        binding.tvEmpty.isVisible = false
                    }
                    is Result.Success -> {
                        binding.progressDecks.isVisible = false
                        adapter.submitList(result.data)
                        binding.tvEmpty.isVisible = result.data.isEmpty()
                    }
                    is Result.Error -> {
                        binding.progressDecks.isVisible = false
                        binding.tvEmpty.isVisible = true
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createState.collect { result ->
                when (result) {
                    is Result.Success -> {
                        viewModel.resetCreateState()
                        val action = NewDeckFragmentDirections.actionNewDeckToEditor(result.data)
                        findNavController().navigate(action)
                    }
                    is Result.Error -> {
                        viewModel.resetCreateState()
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                    }
                    else -> Unit
                }
            }
        }

        viewModel.loadDecks()
    }

    private fun showCreateDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_deck, null)

        val tilName = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilDeckName)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDeckName)
        val cardStandard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStandard)
        val cardCommander = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardCommander)

        var selectedFormat = DeckFormat.STANDARD
        val primaryColor = requireContext().getColor(R.color.manaforge_primary)
        val noneColor = requireContext().getColor(android.R.color.transparent)

        fun highlight(format: DeckFormat) {
            selectedFormat = format
            cardStandard.strokeColor = if (format == DeckFormat.STANDARD) primaryColor else noneColor
            cardCommander.strokeColor = if (format == DeckFormat.COMMANDER) primaryColor else noneColor
        }

        highlight(DeckFormat.STANDARD)
        cardStandard.setOnClickListener { highlight(DeckFormat.STANDARD) }
        cardCommander.setOnClickListener { highlight(DeckFormat.COMMANDER) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create New Deck")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) {
                    Snackbar.make(binding.root, "Enter a deck name", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.create(name = name, format = selectedFormat)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
