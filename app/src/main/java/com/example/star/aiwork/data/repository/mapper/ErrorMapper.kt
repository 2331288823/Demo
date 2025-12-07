package com.example.star.aiwork.data.repository.mapper

import com.example.star.aiwork.data.model.LlmError
import com.example.star.aiwork.infra.network.NetworkException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException

/**
 * 将任意 Throwable 转换为领域层 LlmError
 */
fun Throwable.toLlmError(): LlmError {
    // 如果已经是 LlmError，直接返回
    if (this is LlmError) return this

    // 如果是协程取消，转换为 CancelledError
    if (this is CancellationException) {
        return LlmError.CancelledError("任务已取消", this)
    }

    return when (this) {
        is NetworkException.HttpException -> {
            when (this.code) {
                401, 403 -> LlmError.AuthenticationError(cause = this)
                408 -> LlmError.NetworkError("请求超时", this)
                429 -> LlmError.RateLimitError(cause = this) // 这里直接将 HTTP 429 映射为 RateLimitError
                in 500..599 -> LlmError.ServerError(cause = this)
                else -> LlmError.RequestError(cause = this)
            }
        }
        is NetworkException.TimeoutException -> {
            LlmError.NetworkError("请求超时，请检查网络环境", this)
        }
        is NetworkException.ConnectionException -> {
            LlmError.NetworkError("网络连接异常", this)
        }
        is SocketTimeoutException, is TimeoutException -> {
            LlmError.NetworkError("请求超时，请检查网络环境", this)
        }
        is UnknownHostException -> {
            LlmError.NetworkError("无法解析主机，请检查网络连接", this)
        }
        is IOException -> {
            // 这里可以处理更多细分的 IO 异常
            if (message?.contains("Canceled") == true) {
                LlmError.CancelledError("请求被中断", this)
            } else {
                LlmError.NetworkError("网络连接异常", this)
            }
        }
        else -> {
            LlmError.UnknownError(this.message ?: "未知错误", this)
        }
    }
}
