package com.schoolproject.screenclose.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolproject.screenclose.domain.SessionState
import com.schoolproject.screenclose.domain.StopReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.roundToInt

private val PanelBg = Color(0xF21F2933)
private val Accent = Color(0xFF3DDC97)
private val Danger = Color(0xFFFF6B6B)

@Composable
fun OverlayPanel(
    stateFlow: StateFlow<SessionState>,
    onStart: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val state by stateFlow.collectAsState()
    MaterialTheme {
        Column(
            modifier = Modifier
                .widthIn(min = 200.dp, max = 280.dp)
                .background(PanelBg, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    }
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Screen Close Assistant", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            when (val s = state) {
                SessionState.Idle -> {
                    Text("Ready. Nothing is captured until you start.", color = Color.LightGray, fontSize = 12.sp)
                    Button(onClick = onStart) { Text("Find close button") }
                }
                is SessionState.Capturing -> Busy("Capturing screen (attempt ${s.attempt}/3)…", onCancel)
                is SessionState.Analyzing -> Busy("Analyzing (attempt ${s.attempt}/3)…", onCancel)
                is SessionState.AwaitingConfirmation -> Confirmation(s, onConfirm, onCancel)
                is SessionState.Clicking -> Busy("Tapping…", onCancel)
                is SessionState.CoolingDown -> Busy("Tapped. Cooling down 2 s…", onCancel)
                is SessionState.Stopped -> {
                    Text(stopTitle(s.reason), color = if (s.reason == StopReason.ERROR) Danger else Color.White, fontSize = 13.sp)
                    Text(s.detail.take(160), color = Color.LightGray, fontSize = 12.sp)
                    Button(onClick = onStart) { Text("Find close button") }
                }
            }
        }
    }
}

@Composable
private fun Busy(text: String, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
        Text(text, color = Color.White, fontSize = 12.sp)
    }
    OutlinedButton(onClick = onCancel) { Text("Stop", color = Color.White) }
}

@Composable
private fun Confirmation(s: SessionState.AwaitingConfirmation, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.expiresAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val secondsLeft = max(0L, (s.expiresAtMillis - now + 999) / 1000)
    val pct = (s.response.confidence * 100).roundToInt()
    Text("Found: \"${s.target.label.take(40)}\" ($pct%)", color = Color.White, fontSize = 13.sp)
    Text("Check the green box, then confirm. Expires in ${secondsLeft}s.", color = Color.LightGray, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
            Text("Tap it")
        }
        OutlinedButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
    }
}

private fun stopTitle(reason: StopReason): String = when (reason) {
    StopReason.USER_CANCELLED -> "Stopped."
    StopReason.MAX_ATTEMPTS -> "Done: 3 attempts used."
    StopReason.WAIT -> "Wait: close button not ready yet."
    StopReason.NO_ACTION -> "No closable ad found."
    StopReason.UNSAFE -> "Unsafe to tap. Stopped."
    StopReason.ERROR -> "Error. Stopped."
    StopReason.SCREEN_UNCHANGED -> "Screen unchanged. Stopped."
    StopReason.STALE_SCREEN -> "App changed. Stopped."
    StopReason.CONFIRMATION_EXPIRED -> "Confirmation expired."
    StopReason.CLICK_FAILED -> "Tap failed. Is the accessibility service on?"
    StopReason.CAPTURE_FAILED -> "Capture failed."
}

/** Full-screen, non-touchable layer that outlines the proposed target in screen pixels. */
@Composable
fun MarkerLayer(stateFlow: StateFlow<SessionState>, origin: State<IntOffset>) {
    val state by stateFlow.collectAsState()
    val s = state as? SessionState.AwaitingConfirmation ?: return
    val o = origin.value
    Canvas(modifier = Modifier.fillMaxSize()) {
        val left = (s.target.left - o.x).toFloat()
        val top = (s.target.top - o.y).toFloat()
        val width = (s.target.right - s.target.left).toFloat()
        val height = (s.target.bottom - s.target.top).toFloat()
        val pad = 8.dp.toPx()
        drawRect(
            color = Accent,
            topLeft = Offset(left - pad, top - pad),
            size = Size(width + 2 * pad, height + 2 * pad),
            style = Stroke(width = 4.dp.toPx()),
        )
        val c = Offset(s.target.x - o.x, s.target.y - o.y)
        drawCircle(color = Accent, radius = 6.dp.toPx(), center = c)
        drawCircle(color = Color.White, radius = 10.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
    }
}
