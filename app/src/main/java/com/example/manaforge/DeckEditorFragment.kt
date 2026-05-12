package com.example.manaforge

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.manaforge.R
import com.manaforge.api.ScryfallCardDto
import com.manaforge.api.resolveImageUrl
import com.manaforge.data.models.*
import com.manaforge.databinding.FragmentDeckEditorBinding
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeckEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupSearch()
        setupButtons()
        observeState()

        // If editing existing deck, load it; otherwise wait for createDeck call
        args.deck?.let { viewModel.loadDeck(it) }
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
            viewModel.addCard(dto)
            binding.searchView.setQuery("", false)
            binding.searchView.clearFocus()
        }
        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultsAdapter
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.searchScryfall(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if ((newText?.length ?: 0) >= 3) viewModel.searchScryfall(newText!!)
                binding.recyclerSearchResults.isVisible = !newText.isNullOrBlank()
                return true
            }
        })
    }

    private fun setupButtons() {
        binding.btnValidate.setOnClickListener {
            viewModel.validateCurrentDeck()
        }
        binding.btnSaveDeck.setOnClickListener {
            showSaveDeckDialog()
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
    }

    private fun showSaveDeckDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_deck, null)
        val etName = dialogView.findViewById<EditText>(R.id.etDeckName)
        val spinnerFormat = dialogView.findViewById<Spinner>(R.id.spinnerFormat)

        etName.setText(viewModel.deck.value?.name ?: "")
        val formats = arrayOf("Standard", "Commander")
        spinnerFormat.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, formats)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save Deck")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val format = if (spinnerFormat.selectedItemPosition == 0)
                    DeckFormat.STANDARD else DeckFormat.COMMANDER

                val deck = viewModel.deck.value
                if (deck == null) {
                    viewModel.createNewDeck(userId = 1, name = name, format = format)
                } else {
                    viewModel.editDeck(deck.id, name, format)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeState() {
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
                    Snackbar.make(binding.root, "✓ Deck is valid!", Snackbar.LENGTH_LONG).show()
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
            override fun areContentsTheSame(a: DeckCardWithDetails, b: DeckCardWithDetails) =
                a == b
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

            item.card.imageUrl?.let { url ->
                imgCard.load(url) { crossfade(true) }
            }

            btnMinus.setOnClickListener {
                val newQty = item.quantity - 1
                if (newQty <= 0) onRemove(item.deckCard.id)
                else onQuantityChange(item.deckCard.id, newQty)
            }
            btnPlus.setOnClickListener {
                onQuantityChange(item.deckCard.id, item.quantity + 1)
            }
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
            dto.resolveImageUrl()?.let { url ->
                imgCard.load(url) { crossfade(true) }
            }
            btnAdd.setOnClickListener { onAdd(dto) }
        }
    }
}