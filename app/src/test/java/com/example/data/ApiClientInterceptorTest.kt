package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApiClientInterceptorTest {

    private lateinit var context: Context
    private lateinit var apiClient: ApiClient

    private var capturedRequest: Request? = null
    private var capturedRequestBody: String? = null
    private var mockResponseCode = 200
    private var mockResponseBody = "{}"
    private var mockResponseHeaders = Headers.Builder().build()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        apiClient = ApiClient.getInstance(context)
        
        // Ensure we configure the base URL to trigger Supabase logic branch
        apiClient.tokenStore.baseUrl = "https://abcdefghij.supabase.co"
        apiClient.tokenStore.accessToken = null
        apiClient.tokenStore.refreshToken = null
        
        capturedRequest = null
        capturedRequestBody = null
        mockResponseCode = 200
        mockResponseBody = "{}"
        mockResponseHeaders = Headers.Builder().build()
    }

    private fun buildTestOkHttpClient(): OkHttpClient {
        // We retrieve the interceptor from the actual ApiClient to test it
        // and add a trailing interceptor that halts propagation and captures results.
        return OkHttpClient.Builder()
            .addInterceptor(apiClient.authInterceptor)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                capturedRequest = request
                
                // Read request body to verify correct mapping
                request.body?.let { body ->
                    val buffer = okio.Buffer()
                    body.writeTo(buffer)
                    capturedRequestBody = buffer.readUtf8()
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(mockResponseCode)
                    .message("OK")
                    .headers(mockResponseHeaders)
                    .body(ResponseBody.create("application/json".toMediaTypeOrNull(), mockResponseBody))
                    .build()
            })
            .build()
    }

    @Test
    fun testRegisterRequestAndResponseTransformation() {
        val client = buildTestOkHttpClient()
        
        // Simulated Supabase Response
        mockResponseBody = """
            {
               "access_token": "mock_access_token",
               "refresh_token": "mock_refresh_token",
               "user": {
                  "id": "user-uuid-123456",
                  "email": "user@focusflow.test",
                  "user_metadata": {
                     "display_name": "Focus Hero"
                  }
               }
            }
        """.trimIndent()

        val origPayload = """
            {
               "email": "user@focusflow.test",
               "password": "securepassword",
               "displayName": "Focus Hero"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/auth/register")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), origPayload))
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        // Verify request transformation
        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/auth/v1/signup", req!!.url.encodedPath)
        
        // Verify transformed payload has displayName mapped into Supabase metadata
        val reqBody = capturedRequestBody
        assertNotNull(reqBody)
        assertTrue(reqBody!!.contains("display_name"))
        assertTrue(reqBody.contains("displayName"))
        assertTrue(reqBody.contains("user@focusflow.test"))

        // Verify response payload transformation (camelCased user structure)
        val respBody = response.body?.string()
        assertNotNull(respBody)
        assertTrue(respBody!!.contains("accessToken"))
        assertTrue(respBody.contains("refreshToken"))
        assertTrue(respBody.contains("userId"))
        assertTrue(respBody.contains("displayName"))
        assertTrue(respBody.contains("Focus Hero"))
    }

    @Test
    fun testLoginRequestTransformation() {
        val client = buildTestOkHttpClient()
        
        mockResponseBody = """
            {
               "access_token": "login_access_token",
               "refresh_token": "login_refresh_token",
               "user": {
                  "id": "uuid-987",
                  "email": "user@example.com",
                  "user_metadata": {
                     "display_name": "John Doe"
                  }
               }
            }
        """.trimIndent()

        val origPayload = """
            {
               "email": "user@example.com",
               "password": "mypassword"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/auth/login")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), origPayload))
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/auth/v1/token", req!!.url.encodedPath)
        assertEquals("password", req.url.queryParameter("grant_type"))

        val respBody = response.body?.string()
        assertNotNull(respBody)
        assertTrue(respBody!!.contains("accessToken"))
        assertTrue(respBody.contains("uuid-987"))
    }

    @Test
    fun testLogoutRequestTransformation() {
        val client = buildTestOkHttpClient()
        apiClient.tokenStore.accessToken = "security_logout_access_token"
        
        mockResponseBody = "{}"
        val logoutPayload = """
            {
               "refreshToken": "logout_refresh_token"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/auth/logout")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), logoutPayload))
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/auth/v1/logout", req!!.url.encodedPath)
        assertEquals("Bearer security_logout_access_token", req.header("Authorization"))
    }

    @Test
    fun testProfileDeleteRequest() {
        val client = buildTestOkHttpClient()
        // Base64 of {"sub":"user-uuid-999"} is eyJzdWIiOiJ1c2VyLXV1aWQtOTk5In0=
        val mockJwt = "header.eyJzdWIiOiJ1c2VyLXV1aWQtOTk5In0=.signature"
        apiClient.tokenStore.accessToken = mockJwt

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/profile")
            .delete()
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/rest/v1/profile", req!!.url.encodedPath)
        assertEquals("eq.user-uuid-999", req.url.queryParameter("user_id"))
    }

    @Test
    fun testProfileGetRequestAndResponseTransformation() {
        val client = buildTestOkHttpClient()
        apiClient.tokenStore.accessToken = "dummy_jwt"
        
        // Supabase returns snake_case profile
        mockResponseBody = """
            {
               "user_id": "uuid-777",
               "daily_goal": 90,
               "preferred_apps": ["com.slack", "com.notion"],
               "schedule": [],
               "notifications": true,
               "dark_mode": true,
               "privacy_mode": false,
               "display_name": "Grace Hopper",
               "email": "grace@hopper.com"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/profile")
            .get()
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/rest/v1/profile", req!!.url.encodedPath)
        assertEquals("Bearer dummy_jwt", req.header("Authorization"))
        assertEquals("application/vnd.pgrst.object+json", req.header("Accept"))

        // Verify response rewrites snake_case to camelCase
        val respBody = response.body?.string()
        assertNotNull(respBody)
        assertTrue(respBody!!.contains("userId"))
        assertTrue(respBody.contains("dailyGoal"))
        assertTrue(respBody.contains("preferredApps"))
        assertTrue(respBody.contains("Grace Hopper"))
    }

    @Test
    fun testProfilePatchRequest() {
        val client = buildTestOkHttpClient()
        apiClient.tokenStore.accessToken = "dummy_jwt"

        mockResponseBody = """
            {
               "user_id": "uuid-777",
               "daily_goal": 150
            }
        """.trimIndent()

        val origPatch = """
            {
               "dailyGoal": 150,
               "preferredApps": ["com.test"]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/profile")
            .patch(RequestBody.create("application/json".toMediaTypeOrNull(), origPatch))
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/rest/v1/profile", req!!.url.encodedPath)
        assertEquals("return=representation", req.header("Prefer"))

        // Assert request body converts to snake_case keys for PostgREST compatibility
        val reqBody = capturedRequestBody
        assertNotNull(reqBody)
        assertTrue(reqBody!!.contains("daily_goal"))
        assertTrue(reqBody.contains("preferred_apps"))
        assertFalse(reqBody.contains("dailyGoal"))
    }

    @Test
    fun testCreateSessionRequestWithUserIdInjection() {
        val client = buildTestOkHttpClient()
        
        // Prepare dummy JWT with a recognizable user UUID (sub field)
        // Base64 of {"sub":"user-uuid-999"} is eyJzdWIiOiJ1c2VyLXV1aWQtOTk5In0=
        val mockJwt = "header.eyJzdWIiOiJ1c2VyLXV1aWQtOTk5In0=.signature"
        apiClient.tokenStore.accessToken = mockJwt

        val origSessionPayload = """
            {
               "duration": 45,
               "goal": "Tackle the sprint backlog",
               "allowedApps": []
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/sessions")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), origSessionPayload))
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/rest/v1/sessions", req!!.url.encodedPath)
        assertEquals("return=representation", req.header("Prefer"))

        // Check if user_id was injected from the JWT token and CamelCase converted to snake_case
        val reqBody = capturedRequestBody
        assertNotNull(reqBody)
        assertTrue(reqBody!!.contains("user_id"))
        assertTrue(reqBody.contains("user-uuid-999"))
        assertTrue(reqBody.contains("allowed_apps"))
    }

    @Test
    fun testSessionUpdateById() {
        val client = buildTestOkHttpClient()
        apiClient.tokenStore.accessToken = "dummy_jwt"

        val origUpdate = """
            {
               "completed": true,
               "endedAt": "2026-06-03T12:00:00Z"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/sessions/session-123")
            .patch(RequestBody.create("application/json".toMediaTypeOrNull(), origUpdate))
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/rest/v1/sessions", req!!.url.encodedPath)
        assertEquals("eq.session-123", req.url.queryParameter("id"))

        val reqBody = capturedRequestBody
        assertNotNull(reqBody)
        assertTrue(reqBody!!.contains("completed"))
        assertTrue(reqBody.contains("ended_at"))
    }

    @Test
    fun testSessionStatsRpcMapping() {
        val client = buildTestOkHttpClient()
        
        // JWT payload: {"sub":"user-444"}
        // {"sub":"user-444"} -> Base64 is eyJzdWIiOiJ1c2VyLTQ0NCJ9
        val mockJwt = "header.eyJzdWIiOiJ1c2VyLTQ0NCJ9.signature"
        apiClient.tokenStore.accessToken = mockJwt

        val request = Request.Builder()
            .url("https://abcdefghij.supabase.co/sessions/stats")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), ""))
            .build()

        val response = client.newCall(request).execute()
        assertTrue(response.isSuccessful)

        val req = capturedRequest
        assertNotNull(req)
        assertEquals("/rest/v1/rpc/get_focus_stats", req!!.url.encodedPath)

        val reqBody = capturedRequestBody
        assertNotNull(reqBody)
        assertTrue(reqBody!!.contains("p_user_id"))
        assertTrue(reqBody.contains("user-444"))
    }
}
