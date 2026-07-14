package com.example.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    private val apiClient = ApiClient.getInstance(context)
    private val authRepository = AuthRepository.getInstance(context)

    // DAOs
    private val profileDao = db.userProfileDao()
    private val sessionDao = db.focusSessionDao()
    private val keyValueDao = db.keyValueDao()

    // Key Constants for local settings storage
    companion object {
        const val KEY_IS_LOGGED_IN = "is_logged_in"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_USER_ID = "logged_user_id"
        const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        const val KEY_TIMER_RUNNING = "timer_running"
        const val KEY_TIME_LEFT = "timer_time_left"
        const val KEY_LAST_TICK_TIME = "timer_last_tick_time"
        const val KEY_PROFILE_NEEDS_SYNC = "profile_needs_sync"
        const val KEY_TUTORIAL_DONE = "tutorial_done"
    }

    // --- AUTH FLOW STATES ---
    private val _user = MutableStateFlow<UserProfileEntity?>(null)
    val user: StateFlow<UserProfileEntity?> = _user.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(replay = 0)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val authExceptionHandler = CoroutineExceptionHandler { _, exception ->
        android.util.Log.e("FocusViewModel", "Uncaught exception in auth coroutine", exception)
        _isLoading.value = false
        val errMsg = "An unexpected server or network error occurred: ${exception.localizedMessage ?: "Please check connection & try again."}"
        _authError.value = errMsg
        showSnackbar(errMsg)
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun setAuthError(message: String?) {
        _authError.value = message
    }

    fun getSupabaseUrl(): String {
        return apiClient.tokenStore.baseUrl
    }

    fun getSupabaseAnonKey(): String {
        return apiClient.tokenStore.anonKey ?: ""
    }

    fun updateSupabaseConfig(url: String, anonKey: String) {
        apiClient.tokenStore.baseUrl = url.trim()
        apiClient.tokenStore.anonKey = anonKey.trim()
    }
    
    fun isSupabaseActive(): Boolean {
        val baseUrl = apiClient.tokenStore.baseUrl
        val sbUrl = BuildConfigFieldReader.getFieldString("SUPABASE_URL")
        val isSbUrlValid = sbUrl.isNotEmpty() && sbUrl.startsWith("https://") && !sbUrl.contains("YOUR_SUPABASE")
        
        val isBaseUrlCustomValid = baseUrl.isNotEmpty() && baseUrl.startsWith("https://") && !baseUrl.contains("YOUR_SUPABASE") && !baseUrl.contains("onrender.com")
        
        val sbAnon = BuildConfigFieldReader.getFieldString("SUPABASE_ANON_KEY")
        val isSbAnonValid = sbAnon.isNotEmpty() && !sbAnon.contains("YOUR_SUPABASE")
        val isCustomAnonValid = !apiClient.tokenStore.anonKey.isNullOrEmpty()
        
        return (isSbUrlValid && (isSbAnonValid || isCustomAnonValid)) || (isBaseUrlCustomValid && (isSbAnonValid || isCustomAnonValid))
    }

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isTutorialCompleted = MutableStateFlow(false)
    val isTutorialCompleted: StateFlow<Boolean> = _isTutorialCompleted.asStateFlow()

    // --- ACTIVE FOCUS SESSION LOGIC ---
    private val _activeSession = MutableStateFlow<FocusSessionEntity?>(null)
    val activeSession: StateFlow<FocusSessionEntity?> = _activeSession.asStateFlow()

    private val _timeLeft = MutableStateFlow(0)
    val timeLeft: StateFlow<Int> = _timeLeft.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    // Timer setup states
    var setupGoal = MutableStateFlow("")
    var setupDurationSeconds = MutableStateFlow(1500) // 25 minutes default

    // Selected Apps for Session configuration
    private val _selectedSessionApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedSessionApps: StateFlow<Set<String>> = combine(_selectedSessionApps, _user) { selected, user ->
        val favorites = user?.preferredAppsJson?.let {
            Converters().toStringList(it)
        } ?: emptyList()
        (selected + favorites).toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Dynamic list of packages installed on phone
    private val _installedPackages = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedPackages: StateFlow<List<AppInfo>> = _installedPackages.asStateFlow()

    // Live Stats state loaded from local history or networked server
    private val _stats = MutableStateFlow<StatsResponse>(StatsResponse(emptyList(), 0, 0, 0))
    val stats: StateFlow<StatsResponse> = _stats.asStateFlow()

    private val _prevWeekMinutes = MutableStateFlow(0)
    val prevWeekMinutes: StateFlow<Int> = _prevWeekMinutes.asStateFlow()

    private val _focusSessions = MutableStateFlow<List<FocusSessionEntity>>(emptyList())
    val focusSessions: StateFlow<List<FocusSessionEntity>> = _focusSessions.asStateFlow()

    private var timerJob: Job? = null
    private var sessionStartWallTime = 0L
    private var sessionStartRemainingSeconds = 0

    init {
        // Hydrate authentication/onboarding state on start-up
        hydrateFromStorage()
        loadInstalledApps()

        // Register the authentication state listener
        apiClient.addAuthStateListener(object : ApiClient.AuthStateListener {
            override fun onAuthStateChanged(event: ApiClient.AuthState, session: UserProfileModel?) {
                if (event == ApiClient.AuthState.SIGNED_OUT) {
                    logoutLocally()
                }
            }
        })
    }

    private fun logoutLocally() {
        viewModelScope.launch {
            if (_isAuthenticated.value) {
                profileDao.clearProfile()
                keyValueDao.clear()
                sessionDao.clearSessions()
                _activeSession.value = null
                _user.value = null
                _isAuthenticated.value = false
                _isOnboardingCompleted.value = false
                _isTutorialCompleted.value = false
            }
        }
    }

    private fun hydrateFromStorage() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Check if user is logged in locally
                val loggedInVal = keyValueDao.getValue(KEY_IS_LOGGED_IN)?.value ?: "false"
                val onboardingVal = keyValueDao.getValue(KEY_ONBOARDING_DONE)?.value ?: "false"
                val tutorialVal = keyValueDao.getValue(KEY_TUTORIAL_DONE)?.value ?: "false"
                _isAuthenticated.value = loggedInVal.toBoolean()
                _isOnboardingCompleted.value = onboardingVal.toBoolean()
                _isTutorialCompleted.value = tutorialVal.toBoolean()

                val localProfile = profileDao.getProfile()
                if (localProfile != null) {
                    _user.value = localProfile
                    scheduleAllNotifications(localProfile)
                }

                if (_isAuthenticated.value) {
                    // Sync latest remote profile and retrieve directly from Supabase
                    syncProfileWithRemote()
                }
                refreshAllStats()
                restoreActiveFocusSession()
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun restoreActiveFocusSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeSessionId = keyValueDao.getValue(KEY_ACTIVE_SESSION_ID)?.value ?: ""
                if (activeSessionId.isNotEmpty()) {
                    val session = sessionDao.getSessionById(activeSessionId)
                    if (session != null && !session.completed) {
                        val timerRunningVal = keyValueDao.getValue(KEY_TIMER_RUNNING)?.value ?: "false"
                        val timeLeftVal = keyValueDao.getValue(KEY_TIME_LEFT)?.value ?: "0"
                        val lastTickVal = keyValueDao.getValue(KEY_LAST_TICK_TIME)?.value ?: "0"
                        
                        val isRunning = timerRunningVal.toBoolean()
                        val savedTimeLeft = timeLeftVal.toIntOrNull() ?: 0
                        val lastTick = lastTickVal.toLongOrNull() ?: 0L

                        if (isRunning) {
                            val elapsedSeconds = ((System.currentTimeMillis() - lastTick) / 1000).toInt()
                            val actualTimeLeft = savedTimeLeft - elapsedSeconds
                            if (actualTimeLeft > 0) {
                                withContext(Dispatchers.Main) {
                                    _activeSession.value = session
                                    _timeLeft.value = actualTimeLeft
                                    _isTimerRunning.value = true
                                    
                                    // Save state to memory variables
                                    sessionStartWallTime = lastTick
                                    sessionStartRemainingSeconds = savedTimeLeft

                                    // Start countdown loop
                                    timerJob?.cancel()
                                    timerJob = viewModelScope.launch {
                                        while (_timeLeft.value > 0) {
                                            delay(500)
                                            val elapsed = ((System.currentTimeMillis() - sessionStartWallTime) / 1000).toInt()
                                            _timeLeft.value = (sessionStartRemainingSeconds - elapsed).coerceAtLeast(0)
                                        }
                                        onTimerCompleted()
                                    }
                                }
                            } else {
                                // Session completed in background while app was closed
                                withContext(Dispatchers.Main) {
                                    onTimerCompleted(session)
                                }
                            }
                        } else {
                            // Session was paused when app was closed
                            if (savedTimeLeft > 0) {
                                withContext(Dispatchers.Main) {
                                    _activeSession.value = session
                                    _timeLeft.value = savedTimeLeft
                                    _isTimerRunning.value = false
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleAllNotifications(user: UserProfileEntity) {
        try {
            val list = Converters().toScheduleList(user.scheduleJson)
            for (entry in list) {
                NotificationHelper.scheduleNotification(context, entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun login(emailParam: String, passwordParam: String) {
        viewModelScope.launch(authExceptionHandler) {
            _isLoading.value = true
            _authError.value = null
            try {
                val result = authRepository.login(emailParam, passwordParam)
                when (result) {
                    is Result.Success -> {
                        saveAuthResponse(result.data, isNewUser = false)
                    }
                    is Result.Error -> {
                        val errMsg = result.message
                        _authError.value = errMsg
                        showSnackbar(errMsg)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val crashMsg = "An unexpected error occurred during login: ${e.localizedMessage ?: "Please try again."}"
                _authError.value = crashMsg
                showSnackbar(crashMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(emailParam: String, passwordParam: String, displayNameParam: String) {
        viewModelScope.launch(authExceptionHandler) {
            _isLoading.value = true
            _authError.value = null
            try {
                val result = authRepository.register(emailParam, passwordParam, displayNameParam)
                when (result) {
                    is Result.Success -> {
                        saveAuthResponse(result.data, isNewUser = true)
                    }
                    is Result.Error -> {
                        val errMsg = result.message
                        _authError.value = errMsg
                        showSnackbar(errMsg)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val crashMsg = "An unexpected error occurred during registration: ${e.localizedMessage ?: "Please try again."}"
                _authError.value = crashMsg
                showSnackbar(crashMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun saveAuthResponse(response: AuthResponse, isNewUser: Boolean) {
        try {
            val userVal = response.user
            val tokenVal = response.accessToken
            if (tokenVal.isNullOrEmpty() || userVal == null || userVal.userId.isNullOrEmpty()) {
                _isAuthenticated.value = false
                _authError.value = "Registration successful! Please check your email inbox to confirm your account and then sign in."
                return
            }

            apiClient.tokenStore.accessToken = tokenVal
            apiClient.tokenStore.refreshToken = response.refreshToken

            val currentProfile = _user.value
            val mergedDailyGoal = if (userVal.dailyGoal != null && userVal.dailyGoal != 120) {
                userVal.dailyGoal
            } else {
                currentProfile?.dailyGoal ?: 120
            }
            val mergedPreferredApps = if (userVal.preferredApps != null && userVal.preferredApps.isNotEmpty()) {
                userVal.preferredApps
            } else {
                currentProfile?.preferredAppsJson?.let { Converters().toStringList(it) } ?: emptyList()
            }

            val profile = UserProfileEntity(
                userId = userVal.userId ?: "",
                displayName = userVal.displayName ?: "Focus Member",
                email = userVal.email ?: "",
                dailyGoal = mergedDailyGoal,
                preferredAppsJson = Converters().fromStringList(mergedPreferredApps),
                scheduleJson = Converters().fromScheduleList(userVal.schedule ?: emptyList()),
                notifications = userVal.notifications ?: true,
                darkMode = userVal.darkMode ?: true,
                privacyMode = userVal.privacyMode ?: false,
                photoUrl = userVal.photoUrl
            )

            profileDao.clearProfile()
            profileDao.insertProfile(profile)
            keyValueDao.insertSetting(KeyValueSetting(KEY_IS_LOGGED_IN, "true"))
            keyValueDao.insertSetting(KeyValueSetting(KEY_USER_ID, profile.userId))

            if (!isNewUser) {
                keyValueDao.insertSetting(KeyValueSetting(KEY_ONBOARDING_DONE, "true"))
                _isOnboardingCompleted.value = true
            } else {
                keyValueDao.insertSetting(KeyValueSetting(KEY_ONBOARDING_DONE, "false"))
                _isOnboardingCompleted.value = false
            }

            _user.value = profile
            _isAuthenticated.value = true
            apiClient.notifyAuthStateChanged(ApiClient.AuthState.SIGNED_IN, userVal)
            refreshAllStats()
            // Sync latest remote profile (including schedule) upon login
            syncProfileWithRemote()
        } catch (e: Throwable) {
            e.printStackTrace()
            val errMsg = "Database write error during sign in: ${e.localizedMessage ?: "Please try again."}"
            _authError.value = errMsg
            showSnackbar(errMsg)
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val rToken = apiClient.tokenStore.refreshToken
                if (rToken != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            apiClient.api.logout(LogoutRequest(rToken))
                        } catch (e: Exception) {
                            // Silent failure
                        }
                    }
                }
            } finally {
                apiClient.logoutAndClear()
                profileDao.clearProfile()
                keyValueDao.clear()
                sessionDao.clearSessions()
                _activeSession.value = null
                _user.value = null
                _isAuthenticated.value = false
                _isOnboardingCompleted.value = false
                _isLoading.value = false
            }
        }
    }

    fun completeOnboarding(dailyGoalMin: Int, appEssentials: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val current = _user.value
                val updated = current?.copy(
                    dailyGoal = dailyGoalMin,
                    preferredAppsJson = Converters().fromStringList(appEssentials)
                ) ?: UserProfileEntity(
                    userId = UUID.randomUUID().toString(),
                    displayName = "User",
                    email = "offline@user.com",
                    dailyGoal = dailyGoalMin,
                    preferredAppsJson = Converters().fromStringList(appEssentials),
                    scheduleJson = "[]",
                    notifications = true,
                    darkMode = true,
                    privacyMode = false,
                    photoUrl = null
                )

                profileDao.insertProfile(updated)
                _user.value = updated
                keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

                // Sync with remote Supabase database if configured and logged in
                if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            apiClient.api.updateProfile(mapOf(
                                "dailyGoal" to dailyGoalMin,
                                "preferredApps" to appEssentials
                            ))
                        }
                        keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                keyValueDao.insertSetting(KeyValueSetting(KEY_ONBOARDING_DONE, "true"))
                _isOnboardingCompleted.value = true
                _selectedSessionApps.value = appEssentials.toSet()
                refreshAllStats()
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeTutorial() {
        viewModelScope.launch {
            try {
                keyValueDao.insertSetting(KeyValueSetting(KEY_TUTORIAL_DONE, "true"))
                _isTutorialCompleted.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- PROFILE & SETTINGS CHANGERS ---
    fun updatePreferredApps(apps: List<String>) {
        viewModelScope.launch {
            val u = _user.value ?: UserProfileEntity(
                userId = java.util.UUID.randomUUID().toString(),
                displayName = "User",
                email = "offline@user.com",
                dailyGoal = 120,
                preferredAppsJson = "[]",
                scheduleJson = "[]",
                notifications = true,
                darkMode = true,
                privacyMode = false,
                photoUrl = null
            )
            val updated = u.copy(preferredAppsJson = Converters().fromStringList(apps))
            profileDao.insertProfile(updated)
            _user.value = updated
            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    withContext(Dispatchers.IO) {
                        apiClient.api.updateProfile(mapOf("preferredApps" to apps))
                    }
                    keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun togglePreferredApp(packageName: String) {
        val u = _user.value ?: UserProfileEntity(
            userId = "",
            displayName = "User",
            email = "offline@user.com",
            dailyGoal = 120,
            preferredAppsJson = "[]",
            scheduleJson = "[]",
            notifications = true,
            darkMode = true,
            privacyMode = false,
            photoUrl = null
        )
        val currentList = Converters().toStringList(u.preferredAppsJson).toMutableList()
        if (currentList.contains(packageName)) {
            currentList.remove(packageName)
        } else {
            currentList.add(packageName)
        }
        updatePreferredApps(currentList)
    }

    fun updateDailyGoal(minutes: Int) {
        viewModelScope.launch {
            val u = _user.value ?: return@launch
            val updated = u.copy(dailyGoal = minutes)
            profileDao.insertProfile(updated)
            _user.value = updated
            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    withContext(Dispatchers.IO) {
                        apiClient.api.updateProfile(mapOf("dailyGoal" to minutes))
                    }
                    keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            refreshAllStats()
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            val u = _user.value ?: return@launch
            val updated = u.copy(displayName = name)
            profileDao.insertProfile(updated)
            _user.value = updated
            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    withContext(Dispatchers.IO) {
                        apiClient.api.updateProfile(mapOf("displayName" to name))
                    }
                    keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun syncProfileWithRemote() {
        if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
            viewModelScope.launch {
                try {
                    val needsSync = keyValueDao.getValue(KEY_PROFILE_NEEDS_SYNC)?.value == "true"
                    val local = profileDao.getProfile()
                    
                    if (local != null && needsSync) {
                        try {
                            withContext(Dispatchers.IO) {
                                apiClient.api.updateProfile(mapOf(
                                    "displayName" to local.displayName,
                                    "dailyGoal" to local.dailyGoal,
                                    "preferredApps" to Converters().toStringList(local.preferredAppsJson),
                                    "schedule" to Converters().toScheduleList(local.scheduleJson),
                                    "notifications" to local.notifications,
                                    "darkMode" to local.darkMode,
                                    "privacyMode" to local.privacyMode
                                ))
                            }
                            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Keep needsSync as true and don't pull from remote to prevent overwriting local changes
                            return@launch
                        }
                    }
                    
                    // Now download the latest remote profile
                    var remoteProfile: UserProfileModel? = null
                    try {
                        remoteProfile = withContext(Dispatchers.IO) {
                            apiClient.api.getProfile()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // If fetching fails, the remote profile might not exist.
                        // Let's try to create/insert it using our local profile!
                        val local = profileDao.getProfile()
                        if (local != null) {
                            try {
                                remoteProfile = withContext(Dispatchers.IO) {
                                    apiClient.api.createProfile(mapOf(
                                        "displayName" to local.displayName,
                                        "dailyGoal" to local.dailyGoal,
                                        "preferredApps" to Converters().toStringList(local.preferredAppsJson),
                                        "schedule" to Converters().toScheduleList(local.scheduleJson),
                                        "notifications" to local.notifications,
                                        "darkMode" to local.darkMode,
                                        "privacyMode" to local.privacyMode
                                    ))
                                }
                            } catch (err: Exception) {
                                err.printStackTrace()
                            }
                        }
                    }

                    if (remoteProfile != null) {
                        val profile = UserProfileEntity(
                            userId = remoteProfile.userId ?: "",
                            displayName = remoteProfile.displayName ?: "Focus Member",
                            email = remoteProfile.email ?: "",
                            dailyGoal = remoteProfile.dailyGoal ?: 120,
                            preferredAppsJson = Converters().fromStringList(remoteProfile.preferredApps ?: emptyList()),
                            scheduleJson = Converters().fromScheduleList(remoteProfile.schedule ?: emptyList()),
                            notifications = remoteProfile.notifications ?: true,
                            darkMode = remoteProfile.darkMode ?: true,
                            privacyMode = remoteProfile.privacyMode ?: false,
                            photoUrl = remoteProfile.photoUrl
                        )
                        profileDao.insertProfile(profile)
                        _user.value = profile
                        scheduleAllNotifications(profile)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addScheduleBlock(entry: ScheduleEntry) {
        viewModelScope.launch {
            val u = _user.value ?: return@launch
            val list = Converters().toScheduleList(u.scheduleJson).toMutableList()
            list.add(entry)
            val updatedJson = Converters().fromScheduleList(list)
            val updated = u.copy(scheduleJson = updatedJson)
            profileDao.insertProfile(updated)
            _user.value = updated
            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

            // Schedule the notification alarm
            NotificationHelper.scheduleNotification(context, entry)
            withContext(Dispatchers.Main) {
                try {
                    Toast.makeText(context, "Notification scheduled for Focus block: ${entry.day} ${entry.startTime}", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    withContext(Dispatchers.IO) {
                        apiClient.api.updateProfile(mapOf("schedule" to list))
                    }
                    keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteScheduleBlock(entry: ScheduleEntry) {
        viewModelScope.launch {
            val u = _user.value ?: return@launch
            val list = Converters().toScheduleList(u.scheduleJson).toMutableList()
            list.remove(entry)
            val updatedJson = Converters().fromScheduleList(list)
            val updated = u.copy(scheduleJson = updatedJson)
            profileDao.insertProfile(updated)
            _user.value = updated
            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

            // Cancel the scheduled notification alarm
            NotificationHelper.cancelScheduledNotification(context, entry)
            withContext(Dispatchers.Main) {
                try {
                    Toast.makeText(context, "Focus block notification canceled", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    withContext(Dispatchers.IO) {
                        apiClient.api.updateProfile(mapOf("schedule" to list))
                    }
                    keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun editScheduleBlock(oldEntry: ScheduleEntry, newEntry: ScheduleEntry) {
        viewModelScope.launch {
            val u = _user.value ?: return@launch
            val list = Converters().toScheduleList(u.scheduleJson).toMutableList()
            val index = list.indexOf(oldEntry)
            if (index != -1) {
                list[index] = newEntry
            } else {
                list.add(newEntry)
            }
            val updatedJson = Converters().fromScheduleList(list)
            val updated = u.copy(scheduleJson = updatedJson)
            profileDao.insertProfile(updated)
            _user.value = updated
            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

            // Cancel the old and schedule the new notification alarm
            NotificationHelper.cancelScheduledNotification(context, oldEntry)
            NotificationHelper.scheduleNotification(context, newEntry)
            withContext(Dispatchers.Main) {
                try {
                    Toast.makeText(context, "Focus block updated and notification rescheduled", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    withContext(Dispatchers.IO) {
                        apiClient.api.updateProfile(mapOf("schedule" to list))
                    }
                    keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateSettings(notifs: Boolean, dark: Boolean, privacy: Boolean) {
        viewModelScope.launch {
            val u = _user.value ?: return@launch
            val updated = u.copy(
                notifications = notifs,
                darkMode = dark,
                privacyMode = privacy
            )
            profileDao.insertProfile(updated)
            _user.value = updated
            keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "true"))

            // Broadcast notification state change to resetting the companion web client
            try {
                if (notifs) {
                    FocusWebServer.broadcastEvent("enable_notifications")
                } else {
                    FocusWebServer.broadcastEvent("disable_notifications")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    withContext(Dispatchers.IO) {
                        apiClient.api.updateProfile(mapOf(
                            "notifications" to notifs,
                            "darkMode" to dark,
                            "privacyMode" to privacy
                        ))
                    }
                    keyValueDao.insertSetting(KeyValueSetting(KEY_PROFILE_NEEDS_SYNC, "false"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Delete user profile on Supabase
                if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                    withContext(Dispatchers.IO) {
                        apiClient.api.deleteProfile()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                logout()
            }
        }
    }

    // --- TIMED SESSIONS DRIVER ---

    fun toggleAppSessionSelection(packageName: String) {
        val u = _user.value
        val favorites = u?.preferredAppsJson?.let {
            Converters().toStringList(it)
        } ?: emptyList()
        if (favorites.contains(packageName)) {
            Toast.makeText(context, "Favorite apps are always allowed", Toast.LENGTH_SHORT).show()
            return
        }
        val current = _selectedSessionApps.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedSessionApps.value = current
    }

    fun setAppsSessionSelection(packages: Collection<String>) {
        _selectedSessionApps.value = packages.toSet()
    }

    fun startFocusSession(totalSeconds: Int, goalStr: String, allowedApps: List<String>) {
        viewModelScope.launch {
            timerJob?.cancel()
            _isTimerRunning.value = true
            _timeLeft.value = totalSeconds

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val startTimeStr = sdf.format(Date())

            val favorites = _user.value?.preferredAppsJson?.let {
                Converters().toStringList(it)
            } ?: emptyList()
            val mergedAllowedApps = (allowedApps + favorites).distinct()

            val sessionEntity = FocusSessionEntity(
                id = UUID.randomUUID().toString(),
                userId = _user.value?.userId ?: "offline_user",
                startedAt = startTimeStr,
                duration = totalSeconds,
                goal = goalStr.ifEmpty { "Deep Focus" },
                allowedAppsJson = Converters().fromStringList(mergedAllowedApps),
                completed = false,
                endedAt = null
            )

            // Insert into local Room SQLite db
            sessionDao.insertSession(sessionEntity)
            _activeSession.value = sessionEntity

            // Save state to key-value settings for reliable recovery
            keyValueDao.insertSetting(KeyValueSetting(KEY_ACTIVE_SESSION_ID, sessionEntity.id))
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIMER_RUNNING, "true"))
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIME_LEFT, totalSeconds.toString()))
            keyValueDao.insertSetting(KeyValueSetting(KEY_LAST_TICK_TIME, System.currentTimeMillis().toString()))

            // Save state to memory variables
            sessionStartWallTime = System.currentTimeMillis()
            sessionStartRemainingSeconds = totalSeconds

            // Schedule background AlarmManager to wake up and notify when timer hits zero
            NotificationHelper.scheduleSessionEndAlarm(context, totalSeconds)

            FocusWebServer.broadcastEvent("start", sessionEntity.goal, totalSeconds)

            if (_user.value?.notifications != false) {
                NotificationHelper.sendNotification(
                    getApplication(),
                    "Focus Session Started",
                    "Deep focus session started for ${sessionEntity.goal}."
                )
            }

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    val req = CreateSessionRequest(
                        id = sessionEntity.id,
                        duration = totalSeconds,
                        goal = sessionEntity.goal,
                        allowedApps = mergedAllowedApps
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            apiClient.api.createSession(req)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Start countdown loop
            timerJob = viewModelScope.launch {
                while (_timeLeft.value > 0) {
                    delay(500)
                    val elapsed = ((System.currentTimeMillis() - sessionStartWallTime) / 1000).toInt()
                    _timeLeft.value = (sessionStartRemainingSeconds - elapsed).coerceAtLeast(0)
                }
                onTimerCompleted()
            }
        }
    }

    private fun onTimerCompleted(restoredSession: FocusSessionEntity? = null) {
        viewModelScope.launch {
            _isTimerRunning.value = false
            val session = restoredSession ?: _activeSession.value ?: return@launch
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val endTimeStr = sdf.format(Date())

            val updated = session.copy(completed = true, endedAt = endTimeStr)
            sessionDao.insertSession(updated)

            // Clear state from key-value settings
            keyValueDao.insertSetting(KeyValueSetting(KEY_ACTIVE_SESSION_ID, ""))
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIMER_RUNNING, "false"))
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIME_LEFT, "0"))

            // Cancel background completion alarm
            NotificationHelper.cancelSessionEndAlarm(context)

            FocusWebServer.broadcastEvent("end", session.goal)

            if (_user.value?.notifications != false) {
                NotificationHelper.sendNotification(
                    getApplication(),
                    "Focus Session Completed!",
                    "Fantastic job! You stayed focused for the full duration of: ${session.goal}."
                )
            }

            _activeSession.value = null
            _timeLeft.value = 0

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        try {
                            apiClient.api.updateSession(
                                session.id,
                                UpdateSessionRequest(completed = true, endedAt = endTimeStr)
                            )
                        } catch (e: Exception) {
                            // If update fails because the session wasn't created yet, try creating first
                            val req = CreateSessionRequest(
                                id = session.id,
                                duration = session.duration,
                                goal = session.goal,
                                allowedApps = Converters().toStringList(session.allowedAppsJson)
                            )
                            apiClient.api.createSession(req)
                            apiClient.api.updateSession(
                                session.id,
                                UpdateSessionRequest(completed = true, endedAt = endTimeStr)
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            refreshAllStats()
        }
    }

    fun endFocusSessionPrematurely() {
        viewModelScope.launch {
            timerJob?.cancel()
            _isTimerRunning.value = false
            val session = _activeSession.value ?: return@launch
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val endTimeStr = sdf.format(Date())

            val updated = session.copy(completed = false, endedAt = endTimeStr)
            sessionDao.insertSession(updated)

            // Clear state from key-value settings
            keyValueDao.insertSetting(KeyValueSetting(KEY_ACTIVE_SESSION_ID, ""))
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIMER_RUNNING, "false"))
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIME_LEFT, "0"))

            // Cancel background completion alarm
            NotificationHelper.cancelSessionEndAlarm(context)

            FocusWebServer.broadcastEvent("cancel", session.goal)

            if (_user.value?.notifications != false) {
                NotificationHelper.sendNotification(
                    getApplication(),
                    "Focus Session Canceled",
                    "Session for '${session.goal}' was ended early."
                )
            }

            _activeSession.value = null
            _timeLeft.value = 0

            // Sync with remote Supabase database if configured and logged in
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        try {
                            apiClient.api.updateSession(
                                session.id,
                                UpdateSessionRequest(completed = false, endedAt = endTimeStr)
                            )
                        } catch (e: Exception) {
                            // If update fails because the session wasn't created yet, try creating first
                            val req = CreateSessionRequest(
                                id = session.id,
                                duration = session.duration,
                                goal = session.goal,
                                allowedApps = Converters().toStringList(session.allowedAppsJson)
                            )
                            apiClient.api.createSession(req)
                            apiClient.api.updateSession(
                                session.id,
                                UpdateSessionRequest(completed = false, endedAt = endTimeStr)
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            refreshAllStats()
        }
    }

    fun pauseFocusSession() {
        viewModelScope.launch {
            timerJob?.cancel()
            _isTimerRunning.value = false

            // Save state to key-value settings
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIMER_RUNNING, "false"))
            keyValueDao.insertSetting(KeyValueSetting(KEY_TIME_LEFT, _timeLeft.value.toString()))

            // Cancel background completion alarm
            NotificationHelper.cancelSessionEndAlarm(context)

            FocusWebServer.broadcastEvent("pause")
        }
    }

    fun resumeFocusSession() {
        if (_activeSession.value != null && _timeLeft.value > 0 && !_isTimerRunning.value) {
            _isTimerRunning.value = true
            timerJob?.cancel()

            viewModelScope.launch {
                // Save state to key-value settings
                keyValueDao.insertSetting(KeyValueSetting(KEY_TIMER_RUNNING, "true"))
                keyValueDao.insertSetting(KeyValueSetting(KEY_TIME_LEFT, _timeLeft.value.toString()))
                keyValueDao.insertSetting(KeyValueSetting(KEY_LAST_TICK_TIME, System.currentTimeMillis().toString()))

                // Reschedule background completion alarm with remaining seconds
                NotificationHelper.scheduleSessionEndAlarm(context, _timeLeft.value)

                FocusWebServer.broadcastEvent("resume", _activeSession.value?.goal ?: "", _timeLeft.value)
            }

            sessionStartWallTime = System.currentTimeMillis()
            sessionStartRemainingSeconds = _timeLeft.value

            timerJob = viewModelScope.launch {
                while (_timeLeft.value > 0) {
                    delay(500)
                    val elapsed = ((System.currentTimeMillis() - sessionStartWallTime) / 1000).toInt()
                    _timeLeft.value = (sessionStartRemainingSeconds - elapsed).coerceAtLeast(0)
                }
                onTimerCompleted()
            }
        }
    }

    fun checkTimerSync() {
        viewModelScope.launch {
            if (_isTimerRunning.value && _activeSession.value != null && sessionStartWallTime > 0L) {
                val elapsed = ((System.currentTimeMillis() - sessionStartWallTime) / 1000).toInt()
                val newTimeLeft = (sessionStartRemainingSeconds - elapsed).coerceAtLeast(0)
                _timeLeft.value = newTimeLeft
                if (newTimeLeft == 0) {
                    timerJob?.cancel()
                    onTimerCompleted()
                }
            }
        }
    }

    fun resetFocusSessionTimer() {
        timerJob?.cancel()
        _isTimerRunning.value = false
        val session = _activeSession.value
        if (session != null) {
            _timeLeft.value = session.duration

            viewModelScope.launch {
                // Save state to key-value settings
                keyValueDao.insertSetting(KeyValueSetting(KEY_TIMER_RUNNING, "false"))
                keyValueDao.insertSetting(KeyValueSetting(KEY_TIME_LEFT, session.duration.toString()))

                // Cancel background completion alarm
                NotificationHelper.cancelSessionEndAlarm(context)
            }
        }
    }

    // --- PACKAGES & INGREDIENT QUERIES ---

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val list = try {
                pm.queryIntentActivities(mainIntent, 0)
            } catch (e: Throwable) {
                emptyList()
            }

            val apps = list.mapNotNull { resolve ->
                val act = resolve.activityInfo ?: return@mapNotNull null
                val pkg = act.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                
                val label = try {
                    resolve.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
                
                AppInfo(
                    packageName = pkg,
                    activityName = act.name ?: "",
                    label = label,
                    customLabel = "",
                    isHidden = false,
                    isFavorite = false
                )
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

            val finalApps = if (apps.isEmpty()) {
                AppLibrary.ALL_APPS.map { appDef ->
                    AppInfo(
                        packageName = appDef.androidPackage,
                        activityName = "",
                        label = appDef.name,
                        customLabel = "",
                        isHidden = false,
                        isFavorite = false
                    )
                }
            } else {
                apps
            }

            _installedPackages.value = finalApps
        }
    }

    fun refreshAllStats() {
        syncProfileWithRemote()
        viewModelScope.launch {
            var activeSessionsList: List<FocusSessionEntity> = emptyList()

            // 1. Sync local sessions with Supabase if online (bi-directional synchronization)
            if (isSupabaseActive() && apiClient.tokenStore.accessToken != null) {
                try {
                    val remoteSessions = withContext(Dispatchers.IO) {
                        val localSessions = sessionDao.getAllSessions()
                        val initialRemote = apiClient.api.getSessions()
                        val remoteMap = initialRemote.associateBy { it.id }

                        localSessions.forEach { local ->
                            val remote = remoteMap[local.id]
                            if (remote == null) {
                                // Upload unsynced local sessions
                                try {
                                    val req = CreateSessionRequest(
                                        id = local.id,
                                        duration = local.duration,
                                        goal = local.goal,
                                        allowedApps = Converters().toStringList(local.allowedAppsJson)
                                    )
                                    apiClient.api.createSession(req)
                                    if (local.completed) {
                                        apiClient.api.updateSession(
                                            local.id,
                                            UpdateSessionRequest(completed = true, endedAt = local.endedAt)
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else if (local.completed && !remote.completed) {
                                // Sync local completion status to remote
                                try {
                                    apiClient.api.updateSession(
                                        local.id,
                                        UpdateSessionRequest(completed = true, endedAt = local.endedAt)
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else if (remote.completed && !local.completed) {
                                // Sync remote completion status to local Room (storing/syncing)
                                sessionDao.insertSession(
                                    local.copy(
                                        completed = true,
                                        endedAt = remote.endedAt ?: local.endedAt
                                    )
                                )
                            }
                        }

                        // Re-fetch clean list of remote sessions as the absolute source of truth
                        val cleanRemote = apiClient.api.getSessions()

                        // Sync completely missing remote sessions down to local database (cache only)
                        cleanRemote.forEach { remote ->
                            val local = sessionDao.getSessionById(remote.id)
                            if (local == null) {
                                sessionDao.insertSession(
                                    FocusSessionEntity(
                                        id = remote.id,
                                        userId = remote.userId,
                                        startedAt = remote.startedAt,
                                        duration = remote.duration,
                                        goal = remote.goal,
                                        allowedAppsJson = Converters().fromStringList(remote.allowedApps),
                                        completed = remote.completed,
                                        endedAt = remote.endedAt
                                    )
                                )
                            }
                        }
                        cleanRemote
                    }

                    // Convert remote models to entities for UI
                    activeSessionsList = remoteSessions.map { remote ->
                        FocusSessionEntity(
                            id = remote.id,
                            userId = remote.userId,
                            startedAt = remote.startedAt,
                            duration = remote.duration,
                            goal = remote.goal,
                            allowedAppsJson = Converters().fromStringList(remote.allowedApps),
                            completed = remote.completed,
                            endedAt = remote.endedAt
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback to local storage only if offline/not authenticated, otherwise retrieve purely from Supabase
            if (activeSessionsList.isEmpty()) {
                activeSessionsList = withContext(Dispatchers.IO) {
                    sessionDao.getAllSessions()
                }
            }

            // Expose the retrieved list to the UI StateFlow (without reading local DB flow)
            _focusSessions.value = activeSessionsList

            // 2. Compute stats locally using the retrieved sessions list (direct memory calculation, not retrieving from Room)
            withContext(Dispatchers.Default) {
                try {
                    val calendar = Calendar.getInstance()
                    val sdf = SimpleDateFormat("EEE", Locale.US)
                    val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    
                    // Map days for last 7 days
                    val dailyMap = mutableMapOf<String, Pair<String, Int>>()
                    for (i in 6 downTo 0) {
                        val c = Calendar.getInstance()
                        c.add(Calendar.DAY_OF_YEAR, -i)
                        val label = sdf.format(c.time) // "Mon"
                        val dateStr = dateSdf.format(c.time) // "2026-05-21"
                        dailyMap[dateStr] = Pair(label, 0)
                    }

                    // Map days for previous week (days 13 to 7 ago)
                    val prevWeekMap = mutableMapOf<String, Int>()
                    for (i in 13 downTo 7) {
                        val c = Calendar.getInstance()
                        c.add(Calendar.DAY_OF_YEAR, -i)
                        val dateStr = dateSdf.format(c.time)
                        prevWeekMap[dateStr] = 0
                    }

                    var weekMinTotal = 0
                    var prevWeekMinTotal = 0
                    var countCompleted = 0
                    var sessionSum = 0

                    val presetColors = mapOf(
                        "Deep Work" to "#F97316",
                        "Coding / Dev" to "#3B82F6",
                        "Reading / Study" to "#A855F7",
                        "Writing / Docs" to "#EAB308",
                        "Creative / Art" to "#EC4899",
                        "Meditation / Zen" to "#22C55E"
                    )

                    fun getTaskColor(taskName: String): String {
                        val matched = presetColors.keys.find { it.equals(taskName, ignoreCase = true) }
                        if (matched != null) return presetColors[matched]!!
                        val hexColors = listOf("#F97316", "#3B82F6", "#A855F7", "#EAB308", "#EC4899", "#22C55E", "#14B8A6", "#06B6D4")
                        val idx = Math.abs(taskName.hashCode()) % hexColors.size
                        return hexColors[idx]
                    }

                    val taskMinutesMap = mutableMapOf<String, Int>()

                    activeSessionsList.forEach { ses ->
                        if (ses.completed) {
                            countCompleted++
                            val durationMinutes = ses.duration / 60
                            sessionSum += durationMinutes
                            try {
                                val formattedDateStr = ses.startedAt.substringBefore("T") // "2026-05-21"
                                if (dailyMap.containsKey(formattedDateStr)) {
                                    val entry = dailyMap[formattedDateStr]!!
                                    dailyMap[formattedDateStr] = Pair(entry.first, entry.second + durationMinutes)
                                    weekMinTotal += durationMinutes

                                    val currentTask = ses.goal.trim().ifEmpty { "Deep Focus" }
                                    taskMinutesMap[currentTask] = (taskMinutesMap[currentTask] ?: 0) + durationMinutes
                                } else if (prevWeekMap.containsKey(formattedDateStr)) {
                                    prevWeekMap[formattedDateStr] = prevWeekMap[formattedDateStr]!! + durationMinutes
                                    prevWeekMinTotal += durationMinutes
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    val dailyStatsList = dailyMap.map { (date, pair) ->
                        DailyStatModel(
                            dayLabel = pair.first,
                            dayDate = date,
                            minutes = pair.second
                        )
                    }

                    val taskBreakdownList = taskMinutesMap.map { (taskName, mins) ->
                        TaskStatModel(
                            taskName = taskName,
                            minutes = mins,
                            colorHex = getTaskColor(taskName)
                        )
                    }.sortedByDescending { it.minutes }

                    // Build a complete map of focus minutes for all historical sessions
                    val allSessionsMinutesByDate = mutableMapOf<String, Int>()
                    activeSessionsList.forEach { ses ->
                        if (ses.completed) {
                            val durationMinutes = ses.duration / 60
                            try {
                                val formattedDateStr = ses.startedAt.substringBefore("T")
                                allSessionsMinutesByDate[formattedDateStr] = (allSessionsMinutesByDate[formattedDateStr] ?: 0) + durationMinutes
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // Compute true streak based on consecutive days meeting the daily focus goal
                    val dailyGoalMinutes = _user.value?.dailyGoal ?: 120
                    var streak = 0

                    val todayCalendar = Calendar.getInstance()
                    val todayStr = dateSdf.format(todayCalendar.time)
                    val todayMinutes = allSessionsMinutesByDate[todayStr] ?: 0
                    val todayMet = todayMinutes >= dailyGoalMinutes

                    val yesterdayCalendar = Calendar.getInstance()
                    yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterdayStr = dateSdf.format(yesterdayCalendar.time)
                    val yesterdayMinutes = allSessionsMinutesByDate[yesterdayStr] ?: 0
                    val yesterdayMet = yesterdayMinutes >= dailyGoalMinutes

                    if (todayMet) {
                        var i = 0
                        while (true) {
                            val c = Calendar.getInstance()
                            c.add(Calendar.DAY_OF_YEAR, -i)
                            val dStr = dateSdf.format(c.time)
                            val mins = allSessionsMinutesByDate[dStr] ?: 0
                            if (mins >= dailyGoalMinutes) {
                                streak++
                                i++
                            } else {
                                break
                            }
                        }
                    } else if (yesterdayMet) {
                        var i = 1
                        while (true) {
                            val c = Calendar.getInstance()
                            c.add(Calendar.DAY_OF_YEAR, -i)
                            val dStr = dateSdf.format(c.time)
                            val mins = allSessionsMinutesByDate[dStr] ?: 0
                            if (mins >= dailyGoalMinutes) {
                                streak++
                                i++
                            } else {
                                break
                            }
                        }
                    } else {
                        streak = 0
                    }

                    _prevWeekMinutes.value = prevWeekMinTotal

                    _stats.value = StatsResponse(
                        daily = dailyStatsList,
                        streak = streak,
                        weekMinutes = weekMinTotal,
                        sessionCount = countCompleted,
                        taskBreakdown = taskBreakdownList
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
