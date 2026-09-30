package com.schoolproject.screenclose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.schoolproject.screenclose.auth.AuthState
import com.schoolproject.screenclose.domain.SessionState

data class MainActions(
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
    val onRequestNotifications: () -> Unit,
    val onOpenOverlaySettings: () -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onStartCapture: () -> Unit,
    val onStopCapture: () -> Unit,
)

@Composable
fun MainScreen(state: MainUiState, actions: MainActions) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Screen Close Assistant", style = MaterialTheme.typography.headlineSmall)
            Text(
                "School project: finds an ad's close button in a screenshot you choose to take, " +
                    "shows it to you, and taps it only after you confirm. Screenshots are never saved.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SetupCard(title = "1. Sign in with Microsoft Entra ID", done = state.auth is AuthState.SignedIn, showWhenDone = true) {
                when (val a = state.auth) {
                    AuthState.Initializing -> Text("Initializing…")
                    AuthState.SignedOut -> Button(onClick = actions.onSignIn) { Text("Sign in") }
                    is AuthState.SignedIn -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(a.username, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = actions.onSignOut) { Text("Sign out") }
                    }
                    is AuthState.Failed -> Column {
                        Text("Sign-in unavailable (${a.code.code}): ${a.message}")
                        Button(onClick = actions.onSignIn) { Text("Retry sign in") }
                    }
                }
            }
            SetupCard(title = "2. Allow notifications", done = state.permissions.notifications) {
                Button(onClick = actions.onRequestNotifications) { Text("Allow") }
            }
            SetupCard(title = "3. Allow display over other apps", done = state.permissions.overlay) {
                Button(onClick = actions.onOpenOverlaySettings) { Text("Open settings") }
            }
            SetupCard(
                title = "4. Turn on the \"Screen Close Assistant (confirmed taps)\" accessibility service",
                done = state.permissions.accessibility && state.accessibilityConnected,
            ) {
                Button(onClick = actions.onOpenAccessibilitySettings) { Text("Open accessibility settings") }
            }
            SetupCard(title = "5. Start screen capture", done = state.captureRunning, showWhenDone = true) {
                if (state.captureRunning) {
                    Text("Open the app you want to check, then press \"Find close button\" on the floating panel.")
                    OutlinedButton(onClick = actions.onStopCapture) { Text("Stop capture") }
                } else {
                    Button(onClick = actions.onStartCapture, enabled = state.canStartCapture) { Text("Start capture") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Session", style = MaterialTheme.typography.titleSmall)
                    Text(describe(state.session), style = MaterialTheme.typography.bodySmall)
                    if (state.metrics.isNotEmpty()) {
                        Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
                        state.metrics.entries.sortedBy { it.key }.take(20).forEach { (k, v) ->
                            Text("$k = $v", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(title: String, done: Boolean, showWhenDone: Boolean = false, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (done) "✓ " else "• ") + title, style = MaterialTheme.typography.titleSmall)
            if (!done || showWhenDone) content()
        }
    }
}

private fun describe(s: SessionState): String = when (s) {
    SessionState.Idle -> "Idle"
    is SessionState.Capturing -> "Capturing (attempt ${s.attempt})"
    is SessionState.Analyzing -> "Analyzing request ${s.requestId}"
    is SessionState.AwaitingConfirmation -> "Awaiting your confirmation in the overlay: ${s.target.label}"
    is SessionState.Clicking -> "Tapping"
    is SessionState.CoolingDown -> "Cooling down"
    is SessionState.Stopped -> "Stopped: ${s.reason} — ${s.detail}" +
        (s.lastResponse?.let { "\nLast response: ${it.action} conf=${"%.2f".format(it.confidence)} warnings=${it.warnings}" } ?: "")
}
