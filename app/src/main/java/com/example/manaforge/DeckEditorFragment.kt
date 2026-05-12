package com.example.manaforge

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.example.manaforge.R
import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.Api.resolveImageUrl
import com.example.manaforge.databinding.FragmentDeckEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeckEditorFragment : Fragment() {

    private var _binding: FragmentDeckEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeckEditorViewModel by viewModels()
    private val args: DeckEditorFragmentArgs by navArgs()

    private lateinit var deckCardsAdapter: DeckCardsAdapter
    private lateinit var searchResultsAdapter: SearchResultsAdapter
    private var importProgressDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeckEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deck = args.deck
        if (deck == null) {
            findNavController().popBackStack()
            return
        }
        viewModel.loadDeck(deck)

        setupRecyclerViews()
        setupSearch()
        setupButtons()
        observeState()
    }

    private fun setupRecyclerViews() {
        deckCardsAdapter = DeckCardsAdapter(
            onQuantityChange = { deckCardId, qty -> viewModel.updateQuantity(deckCardId, qty) },
            onRemove = { deckCardId -> viewModel.removeCard(deckCardId) }
        )
        binding.recyclerDeckCards.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deckCardsAdapter
        }

        searchResultsAdapter = SearchResultsAdapter { dto ->
            if (viewModel.isPickingCommander.value) {
                viewModel.setCommander(dto)
            } else {
                viewModel.addCard(dto)
            }
            binding.searchView.setQuery("", false)
            binding.searchView.clearFocus()
            binding.recyclerSearchResults.isVisible = false
        }
        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultsAdapter
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.takeIf { it.length >= 2 }?.let {
                    viewModel.searchScryfall(it, viewModel.isPickingCommander.value)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if ((newText?.length ?: 0) >= 3) {
                    viewModel.searchScryfall(newText!!, viewModel.isPickingCommander.value)
                }
                binding.recyclerSearchResults.isVisible = !newText.isNullOrBlank()
                return true
            }
        })
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnValidate.setOnClickListener {
            viewModel.validateCurrentDeck()
        }

        binding.btnSaveDeck.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnImportDeck.setOnClickListener {
            showImportDialog()
        }

        binding.btnDeleteDeck.setOnClickListener {
            val deckId = viewModel.deck.value?.id ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Deck")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.removeDeck(deckId)
                    findNavController().popBackStack()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnPickCommander.setOnClickListener {
            if (viewModel.isPickingCommander.value) {
                viewModel.cancelPickingCommander()
            } else {
                viewModel.startPickingCommander()
                binding.searchView.isIconified = false
                binding.searchView.requestFocus()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deck.collect { deck ->
                binding.commanderSection.isVisible = deck?.format == DeckFormat.COMMANDER
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isPickingCommander.collect { picking ->
                binding.searchView.queryHint =
                    if (picking) "Search for a Commander (is:commander)…"
                    else "Search cards (Scryfall)…"
                binding.btnPickCommander.text = if (picking) "Cancel" else
                    if (viewModel.commander.value == null) "Set" else "Change"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.commander.collect { card ->
                if (card != null) {
                    binding.layoutNoCommander.isVisible = false
                    binding.layoutCommanderCard.isVisible = true
                    binding.tvCommanderName.text = card.name
                    binding.tvCommanderType.text = card.type ?: ""
                    card.imageUrl?.let { url ->
                        binding.imgCommander.load(url) { crossfade(true) }
                    }
                    binding.btnPickCommander.text = "Change"
                } else {
                    binding.layoutNoCommander.isVisible = true
                    binding.layoutCommanderCard.isVisible = false
                    binding.btnPickCommander.text = "Set"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deckCards.collect { result ->
                when (result) {
                    is Result.Loading -> binding.progressDeckCards.isVisible = true
                    is Result.Success -> {
                        binding.progressDeckCards.isVisible = false
                        deckCardsAdapter.submitList(result.data)
                        binding.tvCardCount.text = "Cards: ${result.data.sumOf { it.quantity }}"
                    }
                    is Result.Error -> {
                        binding.progressDeckCards.isVisible = false
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collect { result ->
                when (result) {
                    is Result.Success -> searchResultsAdapter.submitList(result.data)
                    is Result.Error -> Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
                    else -> Unit
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.validationResult.collect { result ->
                result ?: return@collect
                if (result.isValid) {
                    Snackbar.make(binding.root, "Deck is valid!", Snackbar.LENGTH_LONG).show()
                } else {
                    val msg = result.errors.joinToString("\n• ", prefix = "Issues:\n• ")
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Validation Errors")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.operationState.collect { result ->
                if (result is Result.Error) {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.importState.collect { state ->
                when (state) {
                    is ImportUiState.InProgress -> {
                        if (importProgressDialog == null) {
                            importProgressDialog = AlertDialog.Builder(requireContext())
                                .setTitle("Importing cards…")
                                .setMessage("${state.current} / ${state.total}")
                                .setCancelable(false)
                                .show()
                        } else {
                            importProgressDialog?.setMessage("${state.current} / ${state.total}")
                        }
                    }
                    is ImportUiState.Complete -> {
                        importProgressDialog?.dismiss()
                        importProgressDialog = null
                        val msg = if (state.skipped.isEmpty()) {
                            "Imported ${state.imported} cards successfully."
                        } else {
                            "Imported ${state.imported} cards.\n\nNot found (${state.skipped.size}):\n${state.skipped.joinToString("\n")}"
                        }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Import Complete")
                            .setMessage(msg)
                            .setPositiveButton("OK") { _, _ -> viewModel.resetImportState() }
                            .show()
                    }
                    ImportUiState.Idle -> {
                        importProgressDialog?.dismiss()
                        importProgressDialog = null
                    }
                }
            }
        }
    }

    private fun showImportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_deck, null)
        val etDecklist = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDecklist)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Decklist")
            .setView(dialogView)
            .setPositiveButton("Import") { _, _ ->
                val text = etDecklist.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) viewModel.importFromText(text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─────────────────────────────────────────────
//  Deck Cards Adapter
// ─────────────────────────────────────────────

class DeckCardsAdapter(
    private val onQuantityChange: (Int, Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : ListAdapter<DeckCardWithDetails, DeckCardsAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DeckCardWithDetails>() {
            override fun areItemsTheSame(a: DeckCardWithDetails, b: DeckCardWithDetails) =
                a.deckCard.id == b.deckCard.id
            override fun areContentsTheSame(a: DeckCardWithDetails, b: DeckCardWithDetails) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_deck_card, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCard: ImageView = itemView.findViewById(R.id.imgCard)
        private val tvName: TextView = itemView.findViewById(R.id.tvCardName)
        private val tvType: TextView = itemView.findViewById(R.id.tvCardType)
        private val tvMana: TextView = itemView.findViewById(R.id.tvManaCost)
        private val btnMinus: ImageButton = itemView.findViewById(R.id.btnMinus)
        private val tvQty: TextView = itemView.findViewById(R.id.tvQuantity)
        private val btnPlus: ImageButton = itemView.findViewById(R.id.btnPlus)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)

        fun bind(item: DeckCardWithDetails) {
            tvName.text = item.card.name
            tvType.text = item.card.type ?: ""
            tvMana.text = item.card.manaCost ?: ""
            tvQty.text = item.quantity.toString()
            item.card.imageUrl?.let { url -> imgCard.load(url) { crossfade(true) } }

            btnMinus.setOnClickListener {
                val newQty = item.quantity - 1
                if (newQty <= 0) onRemove(item.deckCard.id)
                else onQuantityChange(item.deckCard.id, newQty)
            }
            btnPlus.setOnClickListener { onQuantityChange(item.deckCard.id, item.quantity + 1) }
            btnRemove.setOnClickListener { onRemove(item.deckCard.id) }
        }
    }
}

// ─────────────────────────────────────────────
//  Search Results Adapter
// ─────────────────────────────────────────────

class SearchResultsAdapter(
    private val onAdd: (ScryfallCardDto) -> Unit
) : ListAdapter<ScryfallCardDto, SearchResultsAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ScryfallCardDto>() {
            override fun areItemsTheSame(a: ScryfallCardDto, b: ScryfallCardDto) = a.id == b.id
            override fun areContentsTheSame(a: ScryfallCardDto, b: ScryfallCardDto) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCard: ImageView = itemView.findViewById(R.id.imgSearchCard)
        private val tvName: TextView = itemView.findViewById(R.id.tvSearchCardName)
        private val tvType: TextView = itemView.findViewById(R.id.tvSearchCardType)
        private val tvMana: TextView = itemView.findViewById(R.id.tvSearchManaCost)
        private val btnAdd: Button = itemView.findViewById(R.id.btnAddCard)

        fun bind(dto: ScryfallCardDto) {
            tvName.text = dto.name
            tvType.text = dto.typeLine ?: ""
            tvMana.text = dto.manaCost ?: ""
            dto.resolveImageUrl()?.let { url -> imgCard.load(url) { crossfade(true) } }
            btnAdd.setOnClickListener { onAdd(dto) }
        }
    }
}
