package com.d2dremote.model

import com.google.gson.Gson

data class TouchEvent(
    val type: String,
    val x: Float,
    val y: Float,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0
) {
    companion object {
        const val ACTION_DOWN = "DOWN"
        const val ACTION_MOVE = "MOVE"
        const val ACTION_UP = "UP"

        private val gson = Gson()

        fun fromJson(json: String): TouchEvent? {
            return try {
                gson.fromJson(json, TouchEvent::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String = gson.toJson(this)
}
