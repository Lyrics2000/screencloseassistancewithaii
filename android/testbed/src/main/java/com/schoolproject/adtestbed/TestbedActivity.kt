package com.schoolproject.adtestbed

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Fake-ad scenarios used to exercise each SCA action deterministically. */
enum class Scenario(val title: String, val countdownSeconds: Int, val closeOverlapsCta: Boolean) {
    CLOSABLE("Closable test ad (expects CLOSE)", 0, false),
    COUNTDOWN("Countdown test ad (expects WAIT)", 15, false),
    TRAP("Close touching Install (expects UNSAFE)", 0, true),
}

class TestbedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var active by remember { mutableStateOf<Scenario?>(null) }
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("SCA Ad Test Bed", style = MaterialTheme.typography.headlineSmall)
                        Text("Plain content screen (expects NO_ACTION). Choose a scenario:")
                        Scenario.entries.forEach { s ->
                            Button(onClick = { active = s }, modifier = Modifier.fillMaxWidth()) { Text(s.title) }
                        }
                    }
                    active?.let { scenario ->
                        FakeInterstitial(
                            scenario = scenario,
                            onClose = { active = null },
                            onCta = { Toast.makeText(this@TestbedActivity, "Test CTA tapped (this is what SCA must avoid)", Toast.LENGTH_SHORT).show() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FakeInterstitial(scenario: Scenario, onClose: () -> Unit, onCta: () -> Unit) {
    var remaining by remember(scenario) { mutableIntStateOf(scenario.countdownSeconds) }
    LaunchedEffect(scenario) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101820)),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ADVERTISEMENT (TEST)", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .size(220.dp)
                    .background(Color(0xFF2E86AB), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sample Game 3000", color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
            }
            Button(onClick = onCta) { Text("Install now") }
        }

        val closeModifier = if (scenario.closeOverlapsCta) {
            Modifier
                .align(Alignment.Center)
                .padding(top = 300.dp, start = 110.dp)
        } else {
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        }
        if (remaining > 0) {
            Text(
                "Skip in ${remaining}s",
                color = Color.LightGray,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp),
            )
        } else {
            TextButton(
                onClick = onClose,
                modifier = closeModifier
                    .size(44.dp)
                    .background(Color(0x66FFFFFF), CircleShape),
            ) {
                Text("✕", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}
