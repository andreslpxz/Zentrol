package com.d2dremote.service

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val onEncodedData: (ByteArray, Int, Int) -> Unit
) {
    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE = "video/avc"
        private const val BIT_RATE = 2_000_000
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 2
    }

    private var encoder: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    @Volatile
    var isEncoding = false
        private set

    fun start() {
        try {
            val alignedWidth = (width + 15) / 16 * 16
            val alignedHeight = (height + 15) / 16 * 16

            val format = MediaFormat.createVideoFormat(MIME_TYPE, alignedWidth, alignedHeight).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }
            isEncoding = true
            Log.i(TAG, "Encoder started: ${alignedWidth}x${alignedHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start encoder", e)
            stop()
        }
    }

    fun drainEncoder() {
        val codec = encoder ?: return
        if (!isEncoding) return

        val bufferInfo = MediaCodec.BufferInfo()
        while (isEncoding) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    val configData = codec.getOutputBuffer(outputIndex)
                    if (configData != null) {
                        val data = ByteArray(bufferInfo.size)
                        configData.get(data)
                        onEncodedData(data, 0, bufferInfo.size)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    continue
                }

                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val data = ByteArray(bufferInfo.size)
                    outputBuffer.get(data)
                    onEncodedData(data, 0, bufferInfo.size)
                }
                codec.releaseOutputBuffer(outputIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } else {
                break
            }
        }
    }

    fun stop() {
        isEncoding = false
        try {
            encoder?.signalEndOfInputStream()
        } catch (_: Exception) {}
        try {
            encoder?.stop()
        } catch (_: Exception) {}
        try {
            encoder?.release()
        } catch (_: Exception) {}
        try {
            inputSurface?.release()
        } catch (_: Exception) {}
        encoder = null
        inputSurface = null
        Log.i(TAG, "Encoder stopped")
    }
}
