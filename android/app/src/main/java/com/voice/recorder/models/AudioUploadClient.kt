package com.voice.recorder.models

import com.google.protobuf.ByteString
import com.voice.recorder.generated.AudioRequest
import com.voice.recorder.generated.AudioServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object AudioUploadClient {

    private const val SERVER_HOST = "192.168.1.34"
    private const val SERVER_PORT = 50051
    private const val TIMEOUT_SECONDS = 30L

    private fun buildChannel(): ManagedChannel =
        ManagedChannelBuilder
            .forAddress(SERVER_HOST, SERVER_PORT)
            .usePlaintext()
            .build()

    suspend fun uploadPcm(
        pcmData: ByteArray,
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitDepth: Int = 16,
    ): Result<String> = withContext(Dispatchers.IO) {
        val channel = buildChannel()
        try {
            val stub = AudioServiceGrpcKt.AudioServiceCoroutineStub(channel)
            val request = AudioRequest.newBuilder()
                .setPcmData(ByteString.copyFrom(pcmData))
                .setSampleRate(sampleRate)
                .setChannels(channels)
                .setBitDepth(bitDepth)
                .build()

            val response = stub
                .withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .uploadAudio(request)

            if (response.success) Result.success(response.message)
            else Result.failure(RuntimeException(response.message))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
