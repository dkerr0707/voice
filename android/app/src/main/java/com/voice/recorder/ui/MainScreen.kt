package com.voice.recorder.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voice.recorder.models.AppViewModel
import com.voice.recorder.models.RecorderState

@Composable
fun MainScreen(audioViewModel: AppViewModel = viewModel()) {
    val state by audioViewModel.state.collectAsState()
    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PermissionChecker.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Voice Recorder",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    if (hasAudioPermission) {
                        audioViewModel.onButtonPressed()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(160.dp, 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (state) {
                        RecorderState.IDLE -> MaterialTheme.colorScheme.primary
                        RecorderState.RECORDING -> Color(0xFFE53935)
                        RecorderState.PLAYING -> Color(0xFF43A047)
                    }
                )
            ) {
                Text(
                    text = when (state) {
                        RecorderState.IDLE -> "Record"
                        RecorderState.RECORDING -> "Stop & Play"
                        RecorderState.PLAYING -> "Stop"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (state) {
                    RecorderState.IDLE -> if (hasAudioPermission) "Ready to record" else "Microphone permission required"
                    RecorderState.RECORDING -> "Recording..."
                    RecorderState.PLAYING -> "Playing back..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
