package com.example.manaforge

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.tabs.TabLayoutMediator
import com.example.manaforge.R
import com.example.manaforge.Deck
import com.example.manaforge.Result
import com.example.manaforge.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var carouselAdapter: CarouselAdapter
    private lateinit var deckListAdapter: DeckListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCarousel()
        setupDeckList()
        observeState()

        viewModel.loadFeaturedDecks()
        viewModel.loadUserDecks()

        binding.fabNewDeck.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_newDeck)
        }

        binding.btnGoToCollection.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_collection)
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }
    }

    private fun setupCarousel() {
        carouselAdapter = CarouselAdapter { deck ->
            val action = HomeFragmentDirections.actionHomeToDeckDetail(deck)
            findNavController().navigate(action)
        }
        binding.viewPagerFeatured.adapter = carouselAdapter
        binding.viewPagerFeatured.offscreenPageLimit = 3

        binding.viewPagerFeatured.setPageTransformer { page, position ->
            val absPos = Math.abs(position)
            page.scaleY = 1f - (absPos * 0.1f)
            page.alpha = 1f - (absPos * 0.3f)
        }

        TabLayoutMediator(binding.dotsIndicator, binding.viewPagerFeatured) { _, _ -> }.attach()
    }

    private fun setupDeckList() {
        deckListAdapter = DeckListAdapter { deck ->
            val action = HomeFragmentDirections.actionHomeToDeckDetail(deck)
            findNavController().navigate(action)
        }
        binding.recyclerMyDecks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deckListAdapter
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.logoutState.collect { result ->
                if (result is Result.Success) {
                    val intent = Intent(requireActivity(), AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.featuredDecks.collect { result ->
                when (result) {
                    is Result.Loading -> binding.progressCarousel.visibility = View.VISIBLE
                    is Result.Success -> {
                        binding.progressCarousel.visibility = View.GONE
                        carouselAdapter.submitList(result.data)
                    }
                    is Result.Error -> {
                        binding.progressCarousel.visibility = View.GONE
                        com.google.android.material.snackbar.Snackbar
                            .make(binding.root, "Could not load featured decks", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userDecks.collect { result ->
                when (result) {
                    is Result.Loading -> binding.progressDecks.visibility = View.VISIBLE
                    is Result.Success -> {
                        binding.progressDecks.visibility = View.GONE
                        deckListAdapter.submitList(result.data)
                    }
                    is Result.Error -> {
                        binding.progressDecks.visibility = View.GONE
                        com.google.android.material.snackbar.Snackbar
                            .make(binding.root, "Could not load your decks: ${result.message}", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class CarouselAdapter(
    private val onClick: (Deck) -> Unit
) : RecyclerView.Adapter<CarouselAdapter.ViewHolder>() {

    private val items = mutableListOf<Deck>()

    fun submitList(list: List<Deck>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carousel_deck, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCover: ImageView = itemView.findViewById(R.id.imgDeckCover)
        private val tvName: TextView = itemView.findViewById(R.id.tvDeckName)
        private val tvFormat: TextView = itemView.findViewById(R.id.tvDeckFormat)

        fun bind(deck: Deck) {
            tvName.text = deck.name
            tvFormat.text = deck.format.value.replaceFirstChar { it.uppercase() }
            if (deck.coverImageUrl != null) {
                imgCover.load(deck.coverImageUrl) { crossfade(true) }
            } else {
                imgCover.setImageDrawable(null)
            }
            itemView.setOnClickListener { onClick(deck) }
        }
    }
}

class DeckListAdapter(
    private val onClick: (Deck) -> Unit
) : RecyclerView.Adapter<DeckListAdapter.ViewHolder>() {

    private val items = mutableListOf<Deck>()

    fun submitList(list: List<Deck>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deck_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCover: ImageView = itemView.findViewById(R.id.imgDeckCover)
        private val tvName: TextView = itemView.findViewById(R.id.tvDeckName)
        private val tvFormat: TextView = itemView.findViewById(R.id.tvFormat)
        private val tvUpdated: TextView = itemView.findViewById(R.id.tvUpdatedAt)

        fun bind(deck: Deck) {
            tvName.text = deck.name
            tvFormat.text = deck.format.value.replaceFirstChar { it.uppercase() }
            tvUpdated.text = if (deck.updatedAt.isNotEmpty()) "Updated: ${deck.updatedAt.take(10)}" else ""
            if (deck.coverImageUrl != null) {
                imgCover.load(deck.coverImageUrl) { crossfade(true) }
            } else {
                imgCover.setImageDrawable(null)
            }
            itemView.setOnClickListener { onClick(deck) }
        }
    }
}