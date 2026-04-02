package com.voice.recorder.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voice.recorder.models.AudioPlayerModel
import com.voice.recorder.models.AudioRecorderModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class RecorderState { IDLE, RECORDING, PLAYING }

class AppViewModel : ViewModel() {

    private val recorderModel = AudioRecorderModel()
    private val playerModel = AudioPlayerModel(
        sampleRate = recorderModel.sampleRate,
        channelConfig = recorderModel.channelConfig,
        audioFormat = recorderModel.audioFormat
    )

    private val _state = MutableStateFlow(RecorderState.IDLE)
    val state: StateFlow<RecorderState> = _state

    private var recordedAudio: ByteArray? = null
    private var recordingJob: Job? = null

    fun onButtonPressed() {
        when (_state.value) {
            RecorderState.IDLE -> startRecording()
            RecorderState.RECORDING -> stopRecordingAndPlay()
            RecorderState.PLAYING -> stopPlayback()
        }
    }

    private fun startRecording() {
        recorderModel.startRecording()
        _state.value = RecorderState.RECORDING

        recordingJob = viewModelScope.launch {
            recordedAudio = recorderModel.readRecordingData()
        }
    }

    private fun stopRecordingAndPlay() {
        recorderModel.stopRecording()
        _state.value = RecorderState.PLAYING

        viewModelScope.launch {
            recordingJob?.join()
            val audio = recordedAudio
            if (audio != null && audio.isNotEmpty()) {
                viewModelScope.launch {
                    AudioUploadClient.uploadPcm(
                        pcmData = audio,
                        sampleRate = recorderModel.sampleRate,
                        channels = 1,
                        bitDepth = 16,
                    ).onFailure { e ->
                        android.util.Log.w("AppViewModel", "Upload failed: ${e.message}")
                    }
                }
                playerModel.play(audio) {
                    _state.value = RecorderState.IDLE
                }
            } else {
                _state.value = RecorderState.IDLE
            }
        }
    }

    private fun stopPlayback() {
        playerModel.stop()
        _state.value = RecorderState.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        recorderModel.stopRecording()
        playerModel.stop()
    }
}
