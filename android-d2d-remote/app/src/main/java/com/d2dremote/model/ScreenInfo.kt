package com.d2dremote.model

data class ScreenInfo(
    val width: Int,
    val height: Int,
    val density: Int,
    val encodedWidth: Int = 0,
    val encodedHeight: Int = 0
) {
    companion object {
        fun fromHeader(header: String): ScreenInfo? {
            return try {
                val parts = header.removePrefix("SCRN:").removeSuffix("\n").split(":")
                val dims = parts[0].split("x")
                val w = dims[0].toInt()
                val h = dims[1].toInt()
                val d = parts[1].toInt()
                val ew: Int
                val eh: Int
                if (parts.size >= 3) {
                    val encDims = parts[2].split("x")
                    ew = encDims[0].toInt()
                    eh = encDims[1].toInt()
                } else {
                    ew = w / 2
                    eh = h / 2
                }
                ScreenInfo(width = w, height = h, density = d, encodedWidth = ew, encodedHeight = eh)
            } catch (e: Exception) {
                null
            }
        }
    }
}
