package com.example.manaforge


import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.example.manaforge.Result
import com.example.manaforge.User
import com.example.manaforge.databinding.ActivityAuthBinding
import com.example.manaforge.LoginUserUseCase
import com.example.manaforge.RegisterUserUseCase
import com.example.manaforge.MainActivity
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
class AuthViewModel @Inject constructor(
    private val loginUser: LoginUserUseCase,
    private val registerUser: RegisterUserUseCase
) : ViewModel() {

    private val _authState = MutableStateFlow<Result<User>?>(null)
    val authState: StateFlow<Result<User>?> = _authState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = Result.Loading
            _authState.value = loginUser(email, password)
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = Result.Loading
            _authState.value = registerUser(username, email, password)
        }
    }
}


@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupActions()
        observeState()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Login"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Register"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val isRegister = tab.position == 1
                binding.tilUsername.visibility = if (isRegister) View.VISIBLE else View.GONE
                binding.btnAuth.text = if (isRegister) "Register" else "Login"
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun setupActions() {
        binding.btnAuth.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val isRegister = binding.tabLayout.selectedTabPosition == 1

            if (email.isEmpty() || password.isEmpty()) {
                Snackbar.make(binding.root, "Please fill in all fields", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegister) {
                val username = binding.etUsername.text.toString().trim()
                if (username.isEmpty()) {
                    Snackbar.make(binding.root, "Username is required", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewModel.register(username, email, password)
            } else {
                viewModel.login(email, password)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.authState.collect { result ->
                result ?: return@collect
                when (result) {
                    is Result.Loading -> {
                        binding.progressAuth.visibility = View.VISIBLE
                        binding.btnAuth.isEnabled = false
                    }
                    is Result.Success -> {
                        binding.progressAuth.visibility = View.GONE
                        startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                        finish()
                    }
                    is Result.Error -> {
                        binding.progressAuth.visibility = View.GONE
                        binding.btnAuth.isEnabled = true
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}