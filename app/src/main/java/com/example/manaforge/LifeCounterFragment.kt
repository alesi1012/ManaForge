package com.example.manaforge

import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LifeCounterViewModel @Inject constructor() : ViewModel() {

    private val _playerCount = MutableStateFlow(4)
    val playerCount: StateFlow<Int> = _playerCount.asStateFlow()

    private val _startingLife = MutableStateFlow(40)
    val startingLife: StateFlow<Int> = _startingLife.asStateFlow()

    private val _lives = MutableStateFlow(listOf(40, 40, 40, 40))
    val lives: StateFlow<List<Int>> = _lives.asStateFlow()

    fun changeLife(index: Int, delta: Int) {
        val current = _lives.value.toMutableList()
        current[index] = current[index] + delta
        _lives.value = current
    }

    fun setPlayerCount(count: Int) {
        _playerCount.value = count.coerceIn(2, 4)
        reset()
    }

    fun setStartingLife(life: Int) {
        _startingLife.value = life
        reset()
    }

    fun reset() {
        val life = _startingLife.value
        _lives.value = listOf(life, life, life, life)
    }
}

@AndroidEntryPoint
class LifeCounterFragment : Fragment() {

    private val viewModel: LifeCounterViewModel by viewModels()

    private lateinit var bottomRow: LinearLayout
    private lateinit var panel4: View

    private lateinit var tvLife1: TextView
    private lateinit var tvLife2: TextView
    private lateinit var tvLife3: TextView
    private lateinit var tvLife4: TextView

    private lateinit var btnMinus1: TextView
    private lateinit var btnPlus1: TextView
    private lateinit var btnMinus2: TextView
    private lateinit var btnPlus2: TextView
    private lateinit var btnMinus3: TextView
    private lateinit var btnPlus3: TextView
    private lateinit var btnMinus4: TextView
    private lateinit var btnPlus4: TextView

    private lateinit var fabMenu: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_life_counter, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomRow = view.findViewById(R.id.bottomRow)
        panel4 = view.findViewById(R.id.panel4)

        tvLife1 = view.findViewById(R.id.tvLife1)
        tvLife2 = view.findViewById(R.id.tvLife2)
        tvLife3 = view.findViewById(R.id.tvLife3)
        tvLife4 = view.findViewById(R.id.tvLife4)

        btnMinus1 = view.findViewById(R.id.btnMinus1)
        btnPlus1  = view.findViewById(R.id.btnPlus1)
        btnMinus2 = view.findViewById(R.id.btnMinus2)
        btnPlus2  = view.findViewById(R.id.btnPlus2)
        btnMinus3 = view.findViewById(R.id.btnMinus3)
        btnPlus3  = view.findViewById(R.id.btnPlus3)
        btnMinus4 = view.findViewById(R.id.btnMinus4)
        btnPlus4  = view.findViewById(R.id.btnPlus4)

        fabMenu = view.findViewById(R.id.fabMenu)

        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        btnMinus1.setOnClickListener { viewModel.changeLife(0, -1) }
        btnPlus1.setOnClickListener  { viewModel.changeLife(0, +1) }
        btnMinus2.setOnClickListener { viewModel.changeLife(1, -1) }
        btnPlus2.setOnClickListener  { viewModel.changeLife(1, +1) }
        btnMinus3.setOnClickListener { viewModel.changeLife(2, -1) }
        btnPlus3.setOnClickListener  { viewModel.changeLife(2, +1) }
        btnMinus4.setOnClickListener { viewModel.changeLife(3, -1) }
        btnPlus4.setOnClickListener  { viewModel.changeLife(3, +1) }

        fabMenu.setOnClickListener { showSettingsDialog() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lives.collect { lives ->
                tvLife1.text = lives[0].toString()
                tvLife2.text = lives[1].toString()
                tvLife3.text = lives[2].toString()
                tvLife4.text = lives[3].toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.playerCount.collect { count ->
                bottomRow.isVisible = count >= 3
                panel4.isVisible = count >= 4
            }
        }
    }

    private fun showSettingsDialog() {
        val ctx = requireContext()
        val lifeValues = intArrayOf(20, 40, 30, 25)
        val lifeLabels = arrayOf("20 (Standard)", "40 (Commander)", "30", "25")

        val currentPlayers = viewModel.playerCount.value
        val currentLife = viewModel.startingLife.value

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
        }

        container.addView(TextView(ctx).apply {
            text = "Players"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        })
        val radioPlayers = RadioGroup(ctx)
        listOf("2 Players", "3 Players", "4 Players").forEachIndexed { i, label ->
            radioPlayers.addView(RadioButton(ctx).apply {
                text = label
                id = i + 2        // id == playerCount value
                isChecked = (i + 2) == currentPlayers
            })
        }
        container.addView(radioPlayers)

        container.addView(TextView(ctx).apply {
            text = "Starting Life"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 24, 0, 0)
        })
        val radioLife = RadioGroup(ctx)
        lifeLabels.forEachIndexed { i, label ->
            radioLife.addView(RadioButton(ctx).apply {
                text = label
                id = i + 10
                isChecked = lifeValues[i] == currentLife
            })
        }
        container.addView(radioLife)

        AlertDialog.Builder(ctx)
            .setTitle("Settings")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val pId = radioPlayers.checkedRadioButtonId
                val lIdx = radioLife.checkedRadioButtonId - 10
                if (pId >= 2) viewModel.setPlayerCount(pId)
                if (lIdx in lifeValues.indices) viewModel.setStartingLife(lifeValues[lIdx])
            }
            .setNeutralButton("Reset") { _, _ -> viewModel.reset() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
