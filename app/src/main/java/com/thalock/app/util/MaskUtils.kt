package com.thalock.app.util

object MaskUtils {

    fun mask(value: String): String {
        if (value.length <= 4) return "\u2022".repeat(value.length)
        val visible = value.takeLast(4)
        val masked = "\u2022".repeat(value.length - 4)
        return "$masked$visible"
    }
}
