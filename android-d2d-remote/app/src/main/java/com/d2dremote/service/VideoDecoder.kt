package com.d2dremote.service

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

class VideoDecoder(
    private val surface: Surface
) {
    companion object {
        private const val TAG = "VideoDecoder"
        private const val MIME_TYPE = "video/avc"
        private val SPS_PREFIX = byteArrayOf(0, 0, 0, 1, 0x67)
        private val PPS_PREFIX = byteArrayOf(0, 0, 0, 1, 0x68)
    }

    private var decoder: MediaCodec? = null

    @Volatile
    var isDecoding = false
        private set

    fun start(width: Int, height: Int) {
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)

            decoder = MediaCodec.createDecoderByType(MIME_TYPE).apply {
                configure(format, surface, null, 0)
                start()
            }
            isDecoding = true
            Log.i(TAG, "Decoder started: ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start decoder", e)
            stop()
        }
    }

    private fun isCodecConfig(data: ByteArray, size: Int): Boolean {
        if (size < 5) return false
        return startsWithPrefix(data, SPS_PREFIX) || startsWithPrefix(data, PPS_PREFIX)
    }

    private fun startsWithPrefix(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) {
            if (data[i] != prefix[i]) return false
        }
        return true
    }

    fun decodeFrame(data: ByteArray, size: Int) {
        val codec = decoder ?: return
        if (!isDecoding) return

        try {
            val flags = if (isCodecConfig(data, size)) {
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG
            } else {
                0
            }

            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(data, 0, size)
                codec.queueInputBuffer(inputIndex, 0, size, System.nanoTime() / 1000, flags)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            while (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, true)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: MediaCodec.CodecException) {
            Log.e(TAG, "Codec exception: ${e.message}")
            if (!e.isRecoverable) {
                stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decode error: ${e.message}")
        }
    }

    fun stop() {
        isDecoding = false
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        decoder = null
        Log.i(TAG, "Decoder stopped")
    }
}
