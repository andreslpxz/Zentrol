package com.d2dremote.model

data class ScreenInfo(
    val width: Int,
    val height: Int,
    val density: Int
) {
    fun toHeader(): ByteArray {
        val header = "SCRN:${width}x${height}:${density}\n"
        return header.toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromHeader(header: String): ScreenInfo? {
            return try {
                val parts = header.removePrefix("SCRN:").removeSuffix("\n").split(":")
                val dims = parts[0].split("x")
                ScreenInfo(
                    width = dims[0].toInt(),
                    height = dims[1].toInt(),
                    density = parts[1].toInt()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
