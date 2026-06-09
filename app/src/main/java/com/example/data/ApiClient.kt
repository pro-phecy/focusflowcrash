package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.io.IOException

// --- NETWORK MODELS ---

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LogoutRequest(
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserProfileModel? = null
)

@JsonClass(generateAdapter = true)
data class RefreshResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null
)

@JsonClass(generateAdapter = true)
data class UserProfileModel(
    val userId: String? = null,
    val dailyGoal: Int? = null,
    val preferredApps: List<String>? = null,
    val schedule: List<ScheduleEntry>? = null,
    val notifications: Boolean? = null,
    val darkMode: Boolean? = null,
    val privacyMode: Boolean? = null,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateSessionRequest(
    val duration: Int,
    val goal: String,
    val allowedApps: List<String>
)

@JsonClass(generateAdapter = true)
data class UpdateSessionRequest(
    val completed: Boolean? = null,
    val endedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class FocusSessionModel(
    val id: String,
    val userId: String,
    val startedAt: String,
    val duration: Int,
    val goal: String,
    val allowedApps: List<String>,
    val completed: Boolean,
    val endedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class DailyStatModel(
    val dayLabel: String,
    val dayDate: String,
    val minutes: Int
)

@JsonClass(generateAdapter = true)
data class StatsResponse(
    val daily: List<DailyStatModel>,
    val streak: Int,
    val weekMinutes: Int,
    val sessionCount: Int
)

@JsonClass(generateAdapter = true)
data class DailyStatModelDto(
    val dayLabel: String? = null,
    val dayDate: String? = null,
    val minutes: Int? = null
)

@JsonClass(generateAdapter = true)
data class StatsResponseDto(
    val daily: List<DailyStatModelDto>? = null,
    val streak: Int? = null,
    val weekMinutes: Int? = null,
    val sessionCount: Int? = null
)

// --- RETROFIT INTERFACE ---

interface FocusFlowApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): retrofit2.Response<Unit>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse

    @GET("profile")
    suspend fun getProfile(): UserProfileModel

    @PATCH("profile")
    suspend fun updateProfile(@Body profile: Map<String, Any?>): UserProfileModel

    @DELETE("profile")
    suspend fun deleteProfile(): retrofit2.Response<Unit>

    @POST("sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): FocusSessionModel

    @GET("sessions")
    suspend fun getSessions(@Query("days") days: Int = 7): List<FocusSessionModel>

    @PATCH("sessions/{id}")
    suspend fun updateSession(@Path("id") id: String, @Body request: UpdateSessionRequest): FocusSessionModel

    @GET("sessions/stats")
    suspend fun getStats(): StatsResponseDto
}

// --- SECURE & LOCAL TOKEN STORAGE ---

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("focusflow_secure_store", Context.MODE_PRIVATE)

    companion object {
        private const val ACCESS_TOKEN = "ff_access_token"
        private const val REFRESH_TOKEN = "ff_refresh_token"
        private const val BASE_URL_KEY = "ff_base_url"
    }

    var accessToken: String?
        get() {
            val v = prefs.getString(ACCESS_TOKEN, null)
            return if (v.isNullOrEmpty()) null else v
        }
        set(value) {
            val v = if (value.isNullOrEmpty()) null else value
            prefs.edit().putString(ACCESS_TOKEN, v).apply()
        }

    var refreshToken: String?
        get() {
            val v = prefs.getString(REFRESH_TOKEN, null)
            return if (v.isNullOrEmpty()) null else v
        }
        set(value) {
            val v = if (value.isNullOrEmpty()) null else value
            prefs.edit().putString(REFRESH_TOKEN, v).apply()
        }

    var baseUrl: String
        get() {
            val sbUrl = try { com.example.BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
            val isSbUrlValid = sbUrl.isNotEmpty() && sbUrl.startsWith("https://") && !sbUrl.contains("YOUR_SUPABASE")
            val defaultUrl = if (isSbUrlValid) sbUrl else "https://focusflow-rn.onrender.com/"
            
            val stored = prefs.getString(BASE_URL_KEY, defaultUrl) ?: defaultUrl
            return if (stored == "http://10.0.2.2:4000/") {
                defaultUrl
            } else {
                stored
            }
        }
        set(value) {
            val formatted = if (value.endsWith("/")) value else "$value/"
            prefs.edit().putString(BASE_URL_KEY, formatted).apply()
        }

    fun clear() {
        prefs.edit().remove(ACCESS_TOKEN).remove(REFRESH_TOKEN).apply()
    }
}

// --- JASON / MOSHI PARSING ERROR UTIL ---

class ApiError(val code: Int, message: String) : IOException(message)

// --- API CLIENT COGNITION ---

class ApiClient private constructor(private val context: Context) {
    val tokenStore = TokenStore(context)
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val mapAdapter = moshi.adapter(Map::class.java)
    private val mapListAdapter = moshi.adapter(List::class.java)

    private fun jsonToMap(json: String): Map<String, Any?>? {
        return try {
            mapAdapter.fromJson(json) as? Map<String, Any?>
        } catch (e: Exception) {
            null
        }
    }

    private fun mapToJson(map: Map<String, Any?>): String {
        return mapAdapter.toJson(map)
    }

    private fun jsonToList(json: String): List<Any?>? {
        return try {
            mapListAdapter.fromJson(json) as? List<Any?>
        } catch (e: Exception) {
            null
        }
    }

    private fun listToJson(list: List<Any?>): String {
        return mapListAdapter.toJson(list)
    }

    private fun camelToSnake(key: String): String {
        val sb = StringBuilder()
        for (char in key) {
            if (char.isUpperCase()) {
                sb.append('_').append(char.lowercaseChar())
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    private fun snakeToCamel(key: String): String {
        val sb = StringBuilder()
        var capitalizeNext = false
        for (char in key) {
            if (char == '_') {
                capitalizeNext = true
            } else {
                if (capitalizeNext) {
                    sb.append(char.uppercaseChar())
                    capitalizeNext = false
                } else {
                    sb.append(char)
                }
            }
        }
        return sb.toString()
    }

    private fun mapKeysToSnake(map: Map<String, Any?>): Map<String, Any?> {
        return map.mapKeys { camelToSnake(it.key) }.mapValues { entry ->
            when (val value = entry.value) {
                is Map<*, *> -> mapKeysToSnake(value as Map<String, Any?>)
                is List<*> -> value.map { if (it is Map<*, *>) mapKeysToSnake(it as Map<String, Any?>) else it }
                else -> value
            }
        }
    }

    private fun mapKeysToCamel(map: Map<String, Any?>): Map<String, Any?> {
        return map.mapKeys { snakeToCamel(it.key) }.mapValues { entry ->
            when (val value = entry.value) {
                is Map<*, *> -> mapKeysToCamel(value as Map<String, Any?>)
                is List<*> -> value.map { if (it is Map<*, *>) mapKeysToCamel(it as Map<String, Any?>) else it }
                else -> value
            }
        }
    }

    private fun extractUserIdFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadDecoded = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
                val regex = """"sub"\s*:\s*"([^"]+)"""".toRegex()
                regex.find(payloadDecoded)?.groupValues?.get(1)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun transformAuthResponse(supabaseJson: String): String {
        val map = jsonToMap(supabaseJson) ?: return supabaseJson
        val accessToken = map["access_token"] as? String ?: ""
        val refreshToken = map["refresh_token"] as? String ?: ""
        
        // Robust extraction: if nested under "user" is not found, fallback to top-level map
        val userMap = (map["user"] as? Map<String, Any?>) ?: map
        val userId = (userMap["id"] as? String) ?: (userMap["userId"] as? String) ?: ""
        val email = userMap["email"] as? String ?: ""
        val userMetadata = userMap["user_metadata"] as? Map<String, Any?> ?: emptyMap()
        
        val displayName = userMetadata["display_name"] as? String 
            ?: userMetadata["displayName"] as? String 
            ?: "Focus Member"

        val responseMap = mapOf(
            "accessToken" to accessToken,
            "refreshToken" to refreshToken,
            "user" to mapOf(
                "userId" to userId,
                "dailyGoal" to 120,
                "preferredApps" to emptyList<String>(),
                "schedule" to emptyList<Any>(),
                "notifications" to true,
                "darkMode" to true,
                "privacyMode" to false,
                "displayName" to displayName,
                "email" to email,
                "photoUrl" to null
            )
        )
        return mapAdapter.toJson(responseMap)
    }

    private fun transformRefreshResponse(supabaseJson: String): String {
        val map = jsonToMap(supabaseJson) ?: return supabaseJson
        val accessToken = map["access_token"] as? String ?: ""
        val refreshToken = map["refresh_token"] as? String ?: ""
        val responseMap = mapOf(
            "accessToken" to accessToken,
            "refreshToken" to refreshToken
        )
        return mapAdapter.toJson(responseMap)
    }

    // Thread-safe lock for token refresh queuing
    private val refreshLock = Any()

    internal val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // Attach stored Bearer accessToken if present
        tokenStore.accessToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        val baseUrlStr = tokenStore.baseUrl
        val isSupabase = baseUrlStr.contains("supabase.co")
        val anonKey = if (isSupabase) {
            try { com.example.BuildConfig.SUPABASE_ANON_KEY } catch (e: Exception) { "" }
        } else ""

        if (isSupabase) {
            builder.header("apikey", anonKey)
        }

        val pathSegments = originalRequest.url.pathSegments
        val path = pathSegments.joinToString("/")
        val method = originalRequest.method

        if (isSupabase) {
            val baseUri = baseUrlStr.toHttpUrlOrNull() ?: originalRequest.url
            val newUrlBuilder = baseUri.newBuilder()

            when {
                path == "auth/register" -> {
                    newUrlBuilder.encodedPath("/auth/v1/signup")
                    builder.url(newUrlBuilder.build())
                    
                    val buffer = okio.Buffer()
                    originalRequest.body?.writeTo(buffer)
                    val bodyString = buffer.readUtf8()
                    val regReq = jsonToMap(bodyString)
                    if (regReq != null) {
                        val email = regReq["email"] as? String ?: ""
                        val password = regReq["password"] as? String ?: ""
                        val displayName = regReq["displayName"] as? String ?: "Focus Member"
                        val sbSignupMap = mapOf(
                            "email" to email,
                            "password" to password,
                            "data" to mapOf(
                                "display_name" to displayName,
                                "displayName" to displayName
                            )
                        )
                        val sbSignupJson = mapToJson(sbSignupMap)
                        builder.post(okhttp3.RequestBody.create(
                            "application/json; charset=utf-8".toMediaTypeOrNull(),
                            sbSignupJson
                        ))
                    }
                }
                path == "auth/login" -> {
                    newUrlBuilder.encodedPath("/auth/v1/token")
                        .addQueryParameter("grant_type", "password")
                    builder.url(newUrlBuilder.build())
                }
                path == "auth/logout" -> {
                    newUrlBuilder.encodedPath("/auth/v1/logout")
                    builder.url(newUrlBuilder.build())
                }
                path == "auth/refresh" -> {
                    newUrlBuilder.encodedPath("/auth/v1/token")
                        .addQueryParameter("grant_type", "refresh_token")
                    builder.url(newUrlBuilder.build())

                    val buffer = okio.Buffer()
                    originalRequest.body?.writeTo(buffer)
                    val bodyString = buffer.readUtf8()
                    val origRef = jsonToMap(bodyString)
                    if (origRef != null) {
                        val refToken = origRef["refreshToken"] as? String ?: ""
                        val sbRefreshMap = mapOf("refresh_token" to refToken)
                        builder.post(okhttp3.RequestBody.create(
                            "application/json; charset=utf-8".toMediaTypeOrNull(),
                            mapToJson(sbRefreshMap)
                        ))
                    }
                }
                path == "profile" -> {
                    newUrlBuilder.encodedPath("/rest/v1/profile")
                    
                    if (method == "GET") {
                        builder.header("Accept", "application/vnd.pgrst.object+json")
                        builder.url(newUrlBuilder.build())
                    } else if (method == "PATCH") {
                        builder.header("Prefer", "return=representation")
                        builder.header("Accept", "application/vnd.pgrst.object+json")
                        builder.url(newUrlBuilder.build())
                        
                        val buffer = okio.Buffer()
                        originalRequest.body?.writeTo(buffer)
                        val bodyString = buffer.readUtf8()
                        val rawMap = jsonToMap(bodyString)
                        if (rawMap != null) {
                            val sbSnakeMap = mapKeysToSnake(rawMap)
                            builder.patch(okhttp3.RequestBody.create(
                                "application/json; charset=utf-8".toMediaTypeOrNull(),
                                mapToJson(sbSnakeMap)
                            ))
                        }
                    } else if (method == "DELETE") {
                        val accessToken = tokenStore.accessToken
                        if (accessToken != null) {
                            val userId = extractUserIdFromToken(accessToken)
                            if (userId != null) {
                                newUrlBuilder.addQueryParameter("user_id", "eq.$userId")
                            }
                        }
                        builder.url(newUrlBuilder.build())
                    } else {
                        builder.url(newUrlBuilder.build())
                    }
                }
                path == "sessions" -> {
                    newUrlBuilder.encodedPath("/rest/v1/sessions")
                    if (method == "POST") {
                        builder.header("Prefer", "return=representation")
                        builder.header("Accept", "application/vnd.pgrst.object+json")

                        val buffer = okio.Buffer()
                        originalRequest.body?.writeTo(buffer)
                        val bodyString = buffer.readUtf8()
                        val rawMap = jsonToMap(bodyString)
                        
                        if (rawMap != null) {
                            val mutMap = rawMap.toMutableMap()
                            val accessToken = tokenStore.accessToken
                            if (accessToken != null) {
                                val userId = extractUserIdFromToken(accessToken)
                                if (userId != null) {
                                    mutMap["user_id"] = userId
                                }
                            }
                            val sbSnakeMap = mapKeysToSnake(mutMap)
                            builder.post(okhttp3.RequestBody.create(
                                "application/json; charset=utf-8".toMediaTypeOrNull(),
                                mapToJson(sbSnakeMap)
                            ))
                        }
                        builder.url(newUrlBuilder.build())
                    } else if (method == "GET") {
                        newUrlBuilder.addQueryParameter("order", "started_at.desc")
                        builder.url(newUrlBuilder.build())
                    }
                }
                path == "sessions/stats" -> {
                    newUrlBuilder.encodedPath("/rest/v1/rpc/get_focus_stats")
                    builder.url(newUrlBuilder.build())
                    
                    val accessToken = tokenStore.accessToken
                    val userId = if (accessToken != null) extractUserIdFromToken(accessToken) else ""
                    val rpcMap = mapOf("p_user_id" to userId)
                    val rpcBody = okhttp3.RequestBody.create(
                        "application/json; charset=utf-8".toMediaTypeOrNull(),
                        mapToJson(rpcMap)
                    )
                    builder.post(rpcBody)
                }
                path.startsWith("sessions/") -> {
                    val sessionId = path.substringAfter("sessions/")
                    newUrlBuilder.encodedPath("/rest/v1/sessions")
                        .addQueryParameter("id", "eq.$sessionId")
                    builder.url(newUrlBuilder.build())

                    if (method == "PATCH") {
                        builder.header("Prefer", "return=representation")
                        builder.header("Accept", "application/vnd.pgrst.object+json")

                        val buffer = okio.Buffer()
                        originalRequest.body?.writeTo(buffer)
                        val bodyString = buffer.readUtf8()
                        val rawMap = jsonToMap(bodyString)
                        if (rawMap != null) {
                            val sbSnakeMap = mapKeysToSnake(rawMap)
                            builder.patch(okhttp3.RequestBody.create(
                                "application/json; charset=utf-8".toMediaTypeOrNull(),
                                mapToJson(sbSnakeMap)
                            ))
                        }
                    }
                }
            }
        }

        val request = builder.build()
        val response = chain.proceed(request)

        if (response.code == 401 && !path.contains("auth/refresh") && !path.contains("auth/v1/token")) {
            response.close()
            
            synchronized(refreshLock) {
                val currentAccessToken = tokenStore.accessToken
                val refreshToken = tokenStore.refreshToken

                if (refreshToken == null) {
                    tokenStore.clear()
                    throw ApiError(401, "Session expired (No refresh token)")
                }

                val freshToken = tokenStore.accessToken
                if (freshToken != currentAccessToken && freshToken != null) {
                    val retryBuilder = originalRequest.newBuilder()
                    if (isSupabase) {
                        retryBuilder.header("apikey", anonKey)
                    }
                    retryBuilder.header("Authorization", "Bearer $freshToken")
                    return@Interceptor chain.proceed(retryBuilder.build())
                }

                val refreshUrl = if (isSupabase) {
                    "${tokenStore.baseUrl.trimEnd('/')}/auth/v1/token?grant_type=refresh_token"
                } else {
                    tokenStore.baseUrl + "auth/refresh"
                }
                val rawMoshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

                val refreshPayload = if (isSupabase) {
                    mapToJson(mapOf("refresh_token" to refreshToken))
                } else {
                    val requestAdapter = rawMoshi.adapter(RefreshRequest::class.java)
                    requestAdapter.toJson(RefreshRequest(refreshToken))
                }

                val refreshBody = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    refreshPayload
                )

                val refreshRequestBuilder = okhttp3.Request.Builder()
                    .url(refreshUrl)
                    .post(refreshBody)
                
                if (isSupabase) {
                    refreshRequestBuilder.header("apikey", anonKey)
                }
                val refreshRequest = refreshRequestBuilder.build()

                val refreshClient = OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                    .build()

                try {
                    val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                    if (refreshResponse.isSuccessful) {
                        val bodyString = refreshResponse.body?.string()
                        if (bodyString != null) {
                            val accToken: String
                            val refToken: String
                            if (isSupabase) {
                                val sMap = jsonToMap(bodyString) ?: emptyMap()
                                accToken = sMap["access_token"] as? String ?: ""
                                refToken = sMap["refresh_token"] as? String ?: ""
                            } else {
                                val responseAdapter = rawMoshi.adapter(RefreshResponse::class.java)
                                val payload = responseAdapter.fromJson(bodyString)
                                accToken = payload?.accessToken ?: ""
                                refToken = payload?.refreshToken ?: ""
                            }

                            if (accToken.isNotEmpty()) {
                                tokenStore.accessToken = accToken
                                tokenStore.refreshToken = refToken
                                refreshResponse.close()

                                val retryBuilder = originalRequest.newBuilder()
                                if (isSupabase) {
                                    retryBuilder.header("apikey", anonKey)
                                }
                                retryBuilder.header("Authorization", "Bearer $accToken")
                                return@Interceptor chain.proceed(retryBuilder.build())
                            }
                        }
                    }

                    val isAuthError = refreshResponse.code in 400..403
                    refreshResponse.close()
                    if (isAuthError) {
                        tokenStore.clear()
                        throw ApiError(401, "Session expired")
                    } else {
                        throw IOException("Temporary server error during token refresh (HTTP ${refreshResponse.code})")
                    }
                } catch (e: Exception) {
                    if (e is ApiError) {
                        throw e
                    }
                    if (e is IOException) {
                        throw e
                    }
                    throw IOException("Connection or server issue during token refresh: ${e.message}", e)
                }
            }
        }

        if (isSupabase && response.isSuccessful) {
            val responseBody = response.body
            if (responseBody != null) {
                val origBodyString = responseBody.string()
                val rewrittenBodyString = when {
                    path == "auth/register" || path == "auth/login" -> {
                        transformAuthResponse(origBodyString)
                    }
                    path == "auth/refresh" -> {
                        transformRefreshResponse(origBodyString)
                    }
                    path == "profile" || path == "sessions" || path.startsWith("sessions/") -> {
                        val item = jsonToMap(origBodyString)
                        if (item != null) {
                            mapToJson(mapKeysToCamel(item))
                        } else {
                            val list = jsonToList(origBodyString)
                            if (list != null) {
                                val camelList = list.map { if (it is Map<*, *>) mapKeysToCamel(it as Map<String, Any?>) else it }
                                listToJson(camelList)
                            } else {
                                origBodyString
                            }
                        }
                    }
                    else -> origBodyString
                }
                
                val newContentType = responseBody.contentType()
                val newBody = okhttp3.ResponseBody.create(newContentType, rewrittenBodyString)
                return@Interceptor response.newBuilder()
                    .body(newBody)
                    .build()
            }
        }

        response
    }

    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(tokenStore.baseUrl)
            .client(buildOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Volatile
    private var cachedRetrofit: Retrofit? = null
    @Volatile
    private var cachedApi: FocusFlowApi? = null
    @Volatile
    private var lastUrlUsed = ""

    val api: FocusFlowApi
        get() {
            val currentUrl = tokenStore.baseUrl
            synchronized(this) {
                if (cachedApi == null || currentUrl != lastUrlUsed) {
                    val retrofit = buildRetrofit()
                    cachedRetrofit = retrofit
                    cachedApi = retrofit.create(FocusFlowApi::class.java)
                    lastUrlUsed = currentUrl
                }
                return cachedApi!!
            }
        }

    companion object {
        @Volatile
        private var INSTANCE: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                val instance = ApiClient(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
