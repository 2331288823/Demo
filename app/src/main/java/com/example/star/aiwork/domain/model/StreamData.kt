package com.example.star.aiwork.domain.model

/**
 * 表示流式传输的数据块
 */
data class StreamData(
    val content: String,
    val status: StreamStatus,
    val errorMessage: String? = null
)

/**
 * 流式传输状态
 */
enum class StreamStatus {
    PROCESSING, // 正在处理
    COMPLETED, // 完成
    ERROR // 错误
}