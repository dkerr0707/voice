package com.voice.recorder.models

import com.google.protobuf.ByteString
import com.voice.recorder.generated.AudioChunk
import com.voice.recorder.generated.AudioServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object AudioUploadClient {

    private const val SERVER_HOST = "192.168.1.34"
    private const val SERVER_PORT = 50051

    private fun buildChannel(): ManagedChannel =
        ManagedChannelBuilder
            .forAddress(SERVER_HOST, SERVER_PORT)
            .usePlaintext()
            .build()

    suspend fun streamPcm(
        chunks: Flow<ByteArray>,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitDepth: Int = 16,
        onTranscription: (text: String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val channel = buildChannel()
        try {
            val stub = AudioServiceGrpcKt.AudioServiceCoroutineStub(channel)
            stub.streamAudio(
                chunks.map { pcm ->
                    AudioChunk.newBuilder()
                        .setPcmData(ByteString.copyFrom(pcm))
                        .setSampleRate(sampleRate)
                        .setChannels(channels)
                        .setBitDepth(bitDepth)
                        .build()
                }
            ).collect { transcriptionChunk ->
                onTranscription(transcriptionChunk.text)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
