package com.example.star.aiwork.ai.util

class KeyRoulette {
    fun next(apiKey: String): String {
        // If the key contains multiple keys separated by comma or newline, rotate them.
        // For simplicity, we just return the key or the first one if split logic is needed later.
        if (apiKey.isBlank()) return ""
        val keys = apiKey.split(Regex("[,\\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) return ""
        // Randomly pick one for load balancing
        return keys.random()
    }

    companion object {
        fun default() = KeyRoulette()
    }
}
