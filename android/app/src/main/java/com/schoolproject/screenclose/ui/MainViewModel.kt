package com.schoolproject.screenclose.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schoolproject.screenclose.auth.AuthState
import com.schoolproject.screenclose.di.AppContainer
import com.schoolproject.screenclose.domain.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val auth: AuthState = AuthState.Initializing,
    val permissions: PermissionStatus = PermissionStatus(notifications = false, overlay = false, accessibility = false),
    val accessibilityConnected: Boolean = false,
    val captureRunning: Boolean = false,
    val session: SessionState = SessionState.Idle,
    val metrics: Map<String, Long> = emptyMap(),
) {
    val canStartCapture: Boolean
        get() = auth is AuthState.SignedIn && permissions.allGranted && accessibilityConnected && !captureRunning
}

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val permissions = MutableStateFlow(PermissionStatus(notifications = false, overlay = false, accessibility = false))
    private val metricsSnapshot = MutableStateFlow<Map<String, Long>>(emptyMap())

    val uiState: StateFlow<MainUiState> = combine(
        container.auth.state,
        permissions,
        container.accessibilityBridge.connected,
        container.captureRegistry.running,
        combine(container.sessionController.state, metricsSnapshot) { s, m -> s to m },
    ) { auth, perms, a11y, running, sessionAndMetrics ->
        MainUiState(auth, perms, a11y, running, sessionAndMetrics.first, sessionAndMetrics.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun refresh(status: PermissionStatus) {
        permissions.value = status
        metricsSnapshot.value = container.metrics.snapshot()
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch { container.auth.signIn(activity) }
    }

    fun signOut() {
        viewModelScope.launch { container.auth.signOut() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)) { "unknown ViewModel $modelClass" }
            return MainViewModel(container) as T
        }
    }
}
