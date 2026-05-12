package com.example.manaforge

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.manaforge.R
import com.example.manaforge.AuthRepository
import com.example.manaforge.databinding.ActivityMainBinding
import com.example.manaforge.AuthActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            authRepository.awaitReady()
            if (!authRepository.isLoggedIn()) {
                startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                finish()
                return@launch
            }
            authRepository.restoreSession()
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.navHostFragment) as NavHostFragment
            binding.bottomNavigation.setupWithNavController(navHostFragment.navController)
        }
    }
}