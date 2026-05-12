package com.example.manaforge

import android.content.Context
import android.util.Log
import com.example.manaforge.Result
import com.example.manaforge.User
import dagger.hilt.android.qualifiers.ApplicationContext
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

private const val TAG = "ManaForge"
private const val PREFS_NAME = "manaforge_prefs"
private const val KEY_USER_ID = "user_id"

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var _currentIntUserId: Int = prefs.getInt(KEY_USER_ID, 0)

    fun currentIntUserId(): Int = _currentIntUserId

    suspend fun currentIntUserIdOrRestore(): Int {
        if (_currentIntUserId > 0) {
            Log.d(TAG, "currentIntUserIdOrRestore: cached = $_currentIntUserId")
            return _currentIntUserId
        }
        Log.d(TAG, "currentIntUserIdOrRestore: not cached, calling restoreSession()")
        restoreSession()
        Log.d(TAG, "currentIntUserIdOrRestore: after restore = $_currentIntUserId")
        return _currentIntUserId
    }

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

            val inserted = supabase.postgrest["users"]
                .insert(
                    User(username = username, email = email, passwordHash = hashPassword(password))
                ) { select() }
                .decodeSingle<User>()

            saveUserId(inserted.id)
            Log.d(TAG, "register: userId set to ${inserted.id}")
            Result.Success(inserted)
        } catch (e: Exception) {
            Log.e(TAG, "register failed", e)
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

                val authEmail = supabase.auth.currentSessionOrNull()?.user?.email ?: email
                val users = supabase.postgrest["users"]
                    .select(Columns.ALL) {
                        filter { eq("email", authEmail) }
                    }
                    .decodeList<User>()

                val user = users.firstOrNull()
                    ?: return@withContext Result.Error("User profile not found. Please register first.")

                saveUserId(user.id)
                Log.d(TAG, "login: userId set to ${user.id}")
                Result.Success(user)
            } catch (e: Exception) {
                Log.e(TAG, "login failed", e)
                Result.Error("Login failed: ${e.message}", e)
            }
        }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
            saveUserId(0)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Logout failed: ${e.message}", e)
        }
    }

    suspend fun awaitReady() {
        supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
    }

    suspend fun restoreSession() {
        awaitReady()
        val savedId = prefs.getInt(KEY_USER_ID, 0)
        if (savedId > 0) {
            _currentIntUserId = savedId
            Log.d(TAG, "restoreSession: restored from prefs, userId=$savedId")
            return
        }
        val authEmail = supabase.auth.currentSessionOrNull()?.user?.email
        Log.d(TAG, "restoreSession: no cached id, trying email lookup for $authEmail")
        if (authEmail == null) return
        try {
            val user = supabase.postgrest["users"]
                .select(Columns.ALL) { filter { eq("email", authEmail) } }
                .decodeList<User>()
                .firstOrNull()
            Log.d(TAG, "restoreSession: email lookup result = $user")
            if (user != null) saveUserId(user.id)
        } catch (e: Exception) {
            Log.e(TAG, "restoreSession email lookup failed", e)
        }
    }

    fun isLoggedIn(): Boolean =
        supabase.auth.currentSessionOrNull() != null

    fun currentUserId(): String? =
        supabase.auth.currentSessionOrNull()?.user?.id

    private fun saveUserId(id: Int) {
        _currentIntUserId = id
        prefs.edit().putInt(KEY_USER_ID, id).apply()
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
