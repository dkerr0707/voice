package com.voice.recorder.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

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

    val playbackEnabled = MutableStateFlow(false)
    val transcription = MutableStateFlow("")

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
        transcription.value = ""
        recorderModel.startRecording()
        _state.value = RecorderState.RECORDING

        val chunkChannel = Channel<ByteArray>(Channel.BUFFERED)

        recordingJob = viewModelScope.launch {
            val buffer = ByteArrayOutputStream()
            recorderModel.recordingFlow().collect { chunk ->
                buffer.write(chunk)
                chunkChannel.send(chunk)
            }
            chunkChannel.close()
            recordedAudio = buffer.toByteArray()
        }

        viewModelScope.launch {
            AudioUploadClient.streamPcm(
                chunks = chunkChannel.receiveAsFlow(),
                sampleRate = recorderModel.sampleRate,
                onTranscription = { text ->
                    transcription.value = text
                }
            ).onFailure { e ->
                android.util.Log.w("AppViewModel", "Stream failed: ${e.message}")
            }
        }
    }

    private fun stopRecordingAndPlay() {
        recorderModel.stopRecording()
        _state.value = RecorderState.PLAYING

        viewModelScope.launch {
            recordingJob?.join()
            val audio = recordedAudio
            if (audio != null && audio.isNotEmpty() && playbackEnabled.value) {
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
