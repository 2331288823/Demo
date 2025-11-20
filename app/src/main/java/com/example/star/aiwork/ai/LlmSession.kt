package com.example.star.aiwork.ai

import android.util.Log

class LlmSession(
    override val modelId: String,
    override val sessionId: String,
    val modelDir: String,
    override val historyList: List<ChatDataItem>?
) : ChatSession {

    override var supportOmni: Boolean = false

    fun generate(query: String): String {
        Log.d("LlmSession", "Generating response for query: $query")
        // Dummy implementation for simulation purposes.
        // In a real app, this would call a local LLM or API.
        return "This is a simulated LLM response for session $sessionId: I received '$query'"
    }
}
