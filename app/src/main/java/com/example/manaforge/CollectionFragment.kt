package com.example.manaforge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.manaforge.Api.ScryfallCardDto
import com.example.manaforge.Api.resolveImageUrl
import com.example.manaforge.databinding.FragmentCollectionBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CollectionFragment : Fragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CollectionViewModel by viewModels()

    private lateinit var collectionAdapter: CollectionCardAdapter
    private lateinit var searchAdapter: CollectionSearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupSearch()
        observeState()
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.fabScanCard.setOnClickListener {
            findNavController().navigate(R.id.action_collection_to_camera)
        }
        viewModel.loadCollection()
    }

    private fun setupAdapters() {
        collectionAdapter = CollectionCardAdapter(
            onDelete = { item -> showDeleteConfirmation(item) }
        )
        binding.recyclerCollection.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = collectionAdapter
        }

        searchAdapter = CollectionSearchAdapter(
            onAdd = { dto -> showAddToCollectionDialog(dto) }
        )
        binding.recyclerSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.searchCards(newText ?: "")
                return true
            }
        })
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.collection.collect { result ->
                when (result) {
                    is Result.Loading -> {
                        binding.progressCollection.visibility = View.VISIBLE
                        binding.tvEmptyState.visibility = View.GONE
                    }
                    is Result.Success -> {
                        binding.progressCollection.visibility = View.GONE
                        collectionAdapter.submitList(result.data)
                        val total = result.data.sumOf { it.collectionCard.quantity }
                        binding.tvCollectionCount.text = "My Collection ($total cards)"
                        binding.tvEmptyState.visibility =
                            if (result.data.isEmpty()) View.VISIBLE else View.GONE
                    }
                    is Result.Error -> {
                        binding.progressCollection.visibility = View.GONE
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collect { result ->
                when (result) {
                    is Result.Loading -> binding.recyclerSearchResults.visibility = View.VISIBLE
                    is Result.Success -> {
                        searchAdapter.submitList(result.data)
                        binding.recyclerSearchResults.visibility =
                            if (result.data.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                    is Result.Error -> binding.recyclerSearchResults.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.operationResult.collect { result ->
                when (result) {
                    is Result.Success -> {
                        Snackbar.make(binding.root, "Collection updated", Snackbar.LENGTH_SHORT).show()
                        viewModel.clearOperationResult()
                    }
                    is Result.Error -> {
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                        viewModel.clearOperationResult()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showAddToCollectionDialog(dto: ScryfallCardDto) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_to_collection, null)

        var quantity = 1
        val tvCardName = dialogView.findViewById<TextView>(R.id.tvDialogCardName)
        val tvQty = dialogView.findViewById<TextView>(R.id.tvQty)
        val btnDecrease = dialogView.findViewById<MaterialButton>(R.id.btnDecreaseQty)
        val btnIncrease = dialogView.findViewById<MaterialButton>(R.id.btnIncreaseQty)
        val spinnerCondition = dialogView.findViewById<Spinner>(R.id.spinnerCondition)
        val checkboxFoil = dialogView.findViewById<CheckBox>(R.id.checkboxFoil)

        tvCardName.text = dto.name

        btnDecrease.setOnClickListener {
            if (quantity > 1) { quantity--; tvQty.text = quantity.toString() }
        }
        btnIncrease.setOnClickListener {
            if (quantity < 99) { quantity++; tvQty.text = quantity.toString() }
        }

        val conditions = CardCondition.labels()
        spinnerCondition.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            conditions
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Add to Collection")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val condition = conditions[spinnerCondition.selectedItemPosition]
                val foil = checkboxFoil.isChecked
                binding.recyclerSearchResults.visibility = View.GONE
                binding.searchView.setQuery("", false)
                binding.searchView.clearFocus()
                viewModel.addToCollection(dto, quantity, foil, condition)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(item: CollectionCardWithDetails) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Card")
            .setMessage("Remove ${item.card.name} from your collection?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeFromCollection(item.collectionCard.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class CollectionCardAdapter(
    private val onDelete: (CollectionCardWithDetails) -> Unit
) : RecyclerView.Adapter<CollectionCardAdapter.ViewHolder>() {

    private val items = mutableListOf<CollectionCardWithDetails>()

    fun submitList(list: List<CollectionCardWithDetails>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collection_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCard: ImageView = itemView.findViewById(R.id.imgCard)
        private val tvName: TextView = itemView.findViewById(R.id.tvCardName)
        private val tvSet: TextView = itemView.findViewById(R.id.tvCardSet)
        private val tvQty: TextView = itemView.findViewById(R.id.tvQuantity)
        private val tvCondition: TextView = itemView.findViewById(R.id.tvCondition)
        private val tvFoil: TextView = itemView.findViewById(R.id.tvFoil)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(item: CollectionCardWithDetails) {
            val card = item.card
            val cc = item.collectionCard
            tvName.text = card.name
            tvSet.text = card.setName ?: ""
            tvQty.text = "x${cc.quantity}"
            tvCondition.text = cc.condition
            tvFoil.visibility = if (cc.foil) View.VISIBLE else View.GONE
            card.imageUrl?.let { imgCard.load(it) { crossfade(true) } }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}

class CollectionSearchAdapter(
    private val onAdd: (ScryfallCardDto) -> Unit
) : RecyclerView.Adapter<CollectionSearchAdapter.ViewHolder>() {

    private val items = mutableListOf<ScryfallCardDto>()

    fun submitList(list: List<ScryfallCardDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCard: ImageView = itemView.findViewById(R.id.imgSearchCard)
        private val tvName: TextView = itemView.findViewById(R.id.tvSearchCardName)
        private val tvType: TextView = itemView.findViewById(R.id.tvSearchCardType)
        private val tvMana: TextView = itemView.findViewById(R.id.tvSearchManaCost)
        private val btnAdd: MaterialButton = itemView.findViewById(R.id.btnAddCard)

        fun bind(dto: ScryfallCardDto) {
            tvName.text = dto.name
            tvType.text = dto.typeLine ?: ""
            tvMana.text = dto.manaCost ?: ""
            dto.resolveImageUrl()?.let { imgCard.load(it) { crossfade(true) } }
            btnAdd.setOnClickListener { onAdd(dto) }
            itemView.setOnClickListener { onAdd(dto) }
        }
    }
}
