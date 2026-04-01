package com.voice.recorder.models

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioPlayerModel(
    private val sampleRate: Int,
    private val channelConfig: Int,
    private val audioFormat: Int
) {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    val outputChannelConfig = AudioFormat.CHANNEL_OUT_MONO

    suspend fun play(data: ByteArray, onComplete: () -> Unit) = withContext(Dispatchers.IO) {
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, outputChannelConfig, audioFormat)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(outputChannelConfig)
                    .setEncoding(audioFormat)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true

        var offset = 0
        val chunkSize = bufferSize
        while (isPlaying && offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            audioTrack?.write(data, offset, end - offset)
            offset = end
        }

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        isPlaying = false
        onComplete()
    }

    fun stop() {
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
