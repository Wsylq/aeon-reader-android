package com.aeonreader.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.cloudflare.CloudflareApiService
import com.aeonreader.data.repository.AuthRepository
import com.aeonreader.data.repository.CloudSyncRepository
import com.aeonreader.data.repository.SyncAllResult
import com.aeonreader.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReadingStats(
    val bookmarkCount: Int = 0,
    val readCount: Int = 0,
    val progressCount: Int = 0
)

sealed class AccountUiState {
    data object Loading : AccountUiState()

    data class LoggedOut(
        val isRegisterMode: Boolean = false,
        val email: String = "",
        val username: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val error: String? = null,
        val isLoading: Boolean = false
    ) : AccountUiState()

    data class LoggedIn(
        val email: String,
        val username: String,
        val isSyncing: Boolean = false,
        val syncResult: String? = null,
        val error: String? = null,
        val stats: ReadingStats = ReadingStats()
    ) : AccountUiState()
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: CloudSyncRepository,
    private val api: CloudflareApiService,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            val isLoggedIn = authRepository.isLoggedIn()
            if (isLoggedIn) {
                val account = authRepository.getAccountInfo()
                if (account != null) {
                    _uiState.value = AccountUiState.LoggedIn(
                        email = account.email,
                        username = account.username
                    )
                    fetchStats()
                } else {
                    _uiState.value = AccountUiState.LoggedOut()
                }
            } else {
                _uiState.value = AccountUiState.LoggedOut()
            }
        }
    }

    private suspend fun fetchStats() {
        val token = userPreferences.getAuthToken() ?: return
        api.getStats(token).onSuccess { stats ->
            val current = _uiState.value as? AccountUiState.LoggedIn ?: return
            _uiState.value = current.copy(
                stats = ReadingStats(
                    bookmarkCount = stats.totalBookmarks,
                    readCount = stats.totalRead,
                    progressCount = stats.totalProgressSaved
                )
            )
        }
    }

    fun updateEmail(email: String) {
        val state = _uiState.value
        if (state is AccountUiState.LoggedOut) {
            _uiState.value = state.copy(email = email, error = null)
        }
    }

    fun updateUsername(username: String) {
        val state = _uiState.value
        if (state is AccountUiState.LoggedOut) {
            _uiState.value = state.copy(username = username, error = null)
        }
    }

    fun updatePassword(password: String) {
        val state = _uiState.value
        if (state is AccountUiState.LoggedOut) {
            _uiState.value = state.copy(password = password, error = null)
        }
    }

    fun updateConfirmPassword(confirmPassword: String) {
        val state = _uiState.value
        if (state is AccountUiState.LoggedOut) {
            _uiState.value = state.copy(confirmPassword = confirmPassword, error = null)
        }
    }

    fun toggleRegisterMode() {
        val state = _uiState.value
        if (state is AccountUiState.LoggedOut) {
            _uiState.value = state.copy(
                isRegisterMode = !state.isRegisterMode,
                error = null,
                password = "",
                confirmPassword = ""
            )
        }
    }

    fun login() {
        val state = _uiState.value as? AccountUiState.LoggedOut ?: return
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password are required")
            return
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = authRepository.login(state.email.trim(), state.password)
            result.fold(
                onSuccess = { account ->
                    _uiState.value = AccountUiState.LoggedIn(
                        email = account.email,
                        username = account.username
                    )
                    fetchStats()
                },
                onFailure = { e ->
                    val current = _uiState.value as? AccountUiState.LoggedOut ?: return@launch
                    _uiState.value = current.copy(isLoading = false, error = e.message ?: "Login failed")
                }
            )
        }
    }

    fun register() {
        val state = _uiState.value as? AccountUiState.LoggedOut ?: return
        if (state.email.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "All fields are required")
            return
        }
        if (state.password.length < 8) {
            _uiState.value = state.copy(error = "Password must be at least 8 characters")
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Passwords do not match")
            return
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = authRepository.register(state.email.trim(), state.username.trim(), state.password)
            result.fold(
                onSuccess = { account ->
                    _uiState.value = AccountUiState.LoggedIn(
                        email = account.email,
                        username = account.username
                    )
                    fetchStats()
                },
                onFailure = { e ->
                    val current = _uiState.value as? AccountUiState.LoggedOut ?: return@launch
                    _uiState.value = current.copy(isLoading = false, error = e.message ?: "Registration failed")
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AccountUiState.LoggedOut()
        }
    }

    fun syncAll() {
        val state = _uiState.value as? AccountUiState.LoggedIn ?: return
        _uiState.value = state.copy(isSyncing = true, syncResult = null, error = null)
        viewModelScope.launch {
            syncRepository.syncAll().fold(
                onSuccess = { result ->
                    val current = _uiState.value as? AccountUiState.LoggedIn ?: return@launch
                    _uiState.value = current.copy(
                        isSyncing = false,
                        syncResult = "Bookmarks: ${result.bookmarksSynced}, Progress: ${result.progressSynced}, History: ${result.historySynced}"
                    )
                    fetchStats()
                },
                onFailure = { e ->
                    val current = _uiState.value as? AccountUiState.LoggedIn ?: return@launch
                    _uiState.value = current.copy(
                        isSyncing = false,
                        error = e.message ?: "Sync failed"
                    )
                }
            )
        }
    }
}
