package com.example.star.aiwork.ai

interface ChatSession {
    var supportOmni: Boolean
    val modelId: String
    val sessionId: String
    val historyList: List<ChatDataItem>?
}
