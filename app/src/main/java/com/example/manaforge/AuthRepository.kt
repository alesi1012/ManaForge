package com.example.manaforge


import com.manaforge.data.models.Result
import com.manaforge.data.models.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    // ── Register ──────────────────────────────────────────────────────────

    /**
     * Registers a new user.
     * 1. Creates an Auth user via Supabase Auth.
     * 2. Inserts a row in the public `users` table with hashed password.
     */
    suspend fun register(
        username: String,
        email: String,
        password: String
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            // 1 – Auth sign-up
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            // 2 – Insert public profile
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

    // ── Login ─────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val user = supabase.postgrest["users"]
                    .select(Columns.ALL) {
                        filter { eq("email", email) }
                    }
                    .decodeSingle<User>()

                Result.Success(user)
            } catch (e: Exception) {
                Result.Error("Login failed: ${e.message}", e)
            }
        }

    // ── Logout ────────────────────────────────────────────────────────────

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Logout failed: ${e.message}", e)
        }
    }

    // ── Current session ───────────────────────────────────────────────────

    fun isLoggedIn(): Boolean =
        supabase.auth.currentSessionOrNull() != null

    fun currentUserId(): String? =
        supabase.auth.currentSessionOrNull()?.user?.id

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}