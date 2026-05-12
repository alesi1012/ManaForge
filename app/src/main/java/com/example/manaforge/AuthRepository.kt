package com.example.manaforge

import com.example.manaforge.Result
import com.example.manaforge.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val user = User(
                username = username,
                email = email,
                passwordHash = hashPassword(password)
            )
            val inserted = supabase.postgrest["users"]
                .insert(user)
                .decodeSingle<User>()

            Result.Success(inserted)
        } catch (e: Exception) {
            Result.Error("Registration failed: ${e.message}", e)
        }
    }

    suspend fun login(email: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val currentUser = supabase.auth.currentSessionOrNull()?.user
                val user = User(
                    id = 0,
                    username = currentUser?.email ?: "",
                    email = currentUser?.email ?: ""
                )

                Result.Success(user)
            } catch (e: Exception) {
                Result.Error("Login failed: ${e.message}", e)
            }
        }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Logout failed: ${e.message}", e)
        }
    }

    suspend fun awaitReady() {
        supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
    }

    fun isLoggedIn(): Boolean =
        supabase.auth.currentSessionOrNull() != null

    fun currentUserId(): String? =
        supabase.auth.currentSessionOrNull()?.user?.id

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}