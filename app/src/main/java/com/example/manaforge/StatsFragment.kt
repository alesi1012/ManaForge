package com.example.manaforge


import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.manaforge.R
import com.manaforge.data.models.*
import com.manaforge.databinding.FragmentStatsBinding
import com.manaforge.domain.usecases.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val getStats: GetDeckStatsUseCase,
    private val getMatches: GetMatchesUseCase,
    private val recordMatch: RecordMatchUseCase
) : ViewModel() {

    private val _stats = MutableStateFlow<Result<DeckStats>>(Result.Loading)
    val stats: StateFlow<Result<DeckStats>> = _stats

    private val _matches = MutableStateFlow<Result<List<Match>>>(Result.Loading)
    val matches: StateFlow<Result<List<Match>>> = _matches

    private val _recordState = MutableStateFlow<Result<Match>?>(null)
    val recordState: StateFlow<Result<Match>?> = _recordState

    fun load(deckId: Int) {
        viewModelScope.launch {
            _stats.value = getStats(deckId)
            _matches.value = getMatches(deckId)
        }
    }

    fun logMatch(
        deckId: Int,
        opponentName: String,
        result: MatchResult,
        damageDealt: Int,
        damageTaken: Int,
        turns: Int
    ) {
        viewModelScope.launch {
            val match = Match(
                deckId = deckId,
                opponentName = opponentName,
                result = result,
                damageDealt = damageDealt,
                damageTaken = damageTaken,
                turnsPlayed = turns
            )
            val r = recordMatch(match)
            _recordState.value = r
            if (r is Result.Success) load(deckId)
        }
    }
}

// ─────────────────────────────────────────────
//  Fragment
// ─────────────────────────────────────────────

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatsViewModel by viewModels()
    private val args: StatsFragmentArgs by navArgs()
    private lateinit var matchesAdapter: MatchesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentStatsBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        matchesAdapter = MatchesAdapter()
        binding.recyclerMatches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = matchesAdapter
        }

        binding.btnLogMatch.setOnClickListener { showLogMatchDialog() }

        observeState()
        viewModel.load(args.deckId)
    }

    private fun showLogMatchDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_log_match, null)
        val etOpponent = dialogView.findViewById<EditText>(R.id.etOpponentName)
        val spinnerResult = dialogView.findViewById<Spinner>(R.id.spinnerResult)
        val etDealt = dialogView.findViewById<EditText>(R.id.etDamageDealt)
        val etTaken = dialogView.findViewById<EditText>(R.id.etDamageTaken)
        val etTurns = dialogView.findViewById<EditText>(R.id.etTurns)

        spinnerResult.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Win", "Loss", "Draw")
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log Match")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val result = when (spinnerResult.selectedItemPosition) {
                    0 -> MatchResult.WIN
                    1 -> MatchResult.LOSS
                    else -> MatchResult.DRAW
                }
                viewModel.logMatch(
                    deckId = args.deckId,
                    opponentName = etOpponent.text.toString(),
                    result = result,
                    damageDealt = etDealt.text.toString().toIntOrNull() ?: 0,
                    damageTaken = etTaken.text.toString().toIntOrNull() ?: 0,
                    turns = etTurns.text.toString().toIntOrNull() ?: 0
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { result ->
                if (result is Result.Success) {
                    val s = result.data
                    binding.tvTotalMatches.text = "Total: ${s.totalMatches}"
                    binding.tvWins.text = "Wins: ${s.wins}"
                    binding.tvLosses.text = "Losses: ${s.losses}"
                    binding.tvDraws.text = "Draws: ${s.draws}"
                    binding.tvWinRate.text = "Win Rate: ${"%.1f".format(s.winRate * 100)}%"
                    binding.tvDamageDealt.text = "Damage Dealt: ${s.totalDamageDealt}"
                    binding.tvDamageTaken.text = "Damage Taken: ${s.totalDamageTaken}"
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.matches.collect { result ->
                if (result is Result.Success) matchesAdapter.submitList(result.data)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─────────────────────────────────────────────
//  Matches Adapter
// ─────────────────────────────────────────────

class MatchesAdapter : ListAdapter<Match, MatchesAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Match>() {
            override fun areItemsTheSame(a: Match, b: Match) = a.id == b.id
            override fun areContentsTheSame(a: Match, b: Match) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOpponent: TextView = itemView.findViewById(R.id.tvOpponentName)
        private val tvResult: TextView = itemView.findViewById(R.id.tvMatchResult)
        private val tvDate: TextView = itemView.findViewById(R.id.tvMatchDate)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvMatchDetails)

        fun bind(match: Match) {
            tvOpponent.text = "vs ${match.opponentName}"
            tvResult.text = match.result.value.replaceFirstChar { it.uppercase() }
            tvResult.setTextColor(
                itemView.context.getColor(
                    when (match.result) {
                        MatchResult.WIN  -> R.color.win_green
                        MatchResult.LOSS -> R.color.loss_red
                        MatchResult.DRAW -> R.color.draw_yellow
                    }
                )
            )
            tvDate.text = match.datePlayed.take(10)
            tvDetails.text = "Dealt ${match.damageDealt} | Took ${match.damageTaken} | ${match.turnsPlayed} turns"
        }
    }
}