package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Unknown error") : Result<Nothing>
}

class AuthRepository private constructor(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val keyValueDao = db.keyValueDao()
    private val apiClient = ApiClient.getInstance(context)

    private fun isSupabaseConfigured(): Boolean {
        val baseUrl = apiClient.tokenStore.baseUrl
        val sbUrl = BuildConfigFieldReader.getFieldString("SUPABASE_URL")
        val isSbUrlValid = sbUrl.isNotEmpty() && sbUrl.startsWith("https://") && !sbUrl.contains("YOUR_SUPABASE")
        
        val isBaseUrlCustomValid = baseUrl.isNotEmpty() && baseUrl.startsWith("https://") && !baseUrl.contains("YOUR_SUPABASE") && !baseUrl.contains("onrender.com")
        
        val sbAnon = BuildConfigFieldReader.getFieldString("SUPABASE_ANON_KEY")
        val isSbAnonValid = sbAnon.isNotEmpty() && !sbAnon.contains("YOUR_SUPABASE")
        val isCustomAnonValid = !apiClient.tokenStore.anonKey.isNullOrEmpty()
        
        return (isSbUrlValid && (isSbAnonValid || isCustomAnonValid)) || (isBaseUrlCustomValid && (isSbAnonValid || isCustomAnonValid))
    }

    suspend fun login(emailParam: String, passwordParam: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val emailNormalized = emailParam.trim().lowercase()
        if (emailNormalized.isEmpty() || passwordParam.isEmpty()) {
            return@withContext Result.Error(
                Exception("InvalidInput"),
                "Please enter both email and password."
            )
        }

        if (!isSupabaseConfigured()) {
            return@withContext Result.Error(
                Exception("SupabaseNotConfigured"),
                "Supabase is not configured yet. Please configure Supabase URL and Anon Key."
            )
        }

        try {
            val response = apiClient.api.login(LoginRequest(emailParam, passwordParam))
            Result.Success(response)
        } catch (e: Exception) {
            val code = when (e) {
                is ApiError -> e.code
                is retrofit2.HttpException -> e.code()
                else -> -1
            }
            val isCredentialsError = code == 400 || code == 401 || code == 403
            val errMsg = if (isCredentialsError) {
                "Incorrect email or password."
            } else {
                "Authentication failed: ${e.localizedMessage ?: "Network or database connection issue"}"
            }
            Result.Error(e, errMsg)
        }
    }

    suspend fun register(emailParam: String, passwordParam: String, displayNameParam: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        val emailNormalized = emailParam.trim().lowercase()
        if (emailNormalized.isEmpty() || !emailNormalized.contains("@")) {
            return@withContext Result.Error(
                Exception("InvalidEmail"),
                "Please provide a valid email address."
            )
        }
        if (passwordParam.length < 6) {
            return@withContext Result.Error(
                Exception("WeakPassword"),
                "Password must be at least 6 characters."
            )
        }

        if (!isSupabaseConfigured()) {
            return@withContext Result.Error(
                Exception("SupabaseNotConfigured"),
                "Supabase is not configured yet. Please configure Supabase URL and Anon Key."
            )
        }

        try {
            val response = apiClient.api.register(RegisterRequest(emailParam, passwordParam, displayNameParam))
            Result.Success(response)
        } catch (e: Exception) {
            val code = when (e) {
                is ApiError -> e.code
                is retrofit2.HttpException -> e.code()
                else -> -1
            }
            val errMsg = if (code != -1) {
                when (code) {
                    422 -> "Supabase: Password too weak or invalid email address."
                    409 -> "Supabase: This email address is already registered."
                    else -> "Supabase registration failed ($code): ${e.localizedMessage ?: "Unknown database error"}"
                }
            } else {
                "Registration failed: ${e.localizedMessage ?: "Network or database connection issue"}"
            }
            Result.Error(e, errMsg)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AuthRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
