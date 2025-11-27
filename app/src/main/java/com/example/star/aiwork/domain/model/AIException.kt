package com.example.star.aiwork.domain.model

/**
 * 统一的 AI 业务异常类。
 * 用于将 HTTP 状态码或底层网络错误转换为 UI 层可理解的错误类型。
 */
sealed class AIException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    // 401: API Key 错误或过期
    class AuthenticationError(message: String = "API Key 无效或过期，请检查设置") : AIException(message)

    // 429: 请求太快
    class RateLimitError(message: String = "请求过于频繁，请稍后重试") : AIException(message)

    // 429 (特殊): 余额不足 (通常 429 也包含额度问题，需要根据 error body 判断)
    class InsufficientQuotaError(message: String = "账户余额不足，请充值") : AIException(message)

    // 500+: 服务商挂了
    class ServerError(message: String = "服务商服务器异常，请稍后再试") : AIException(message)

    // 400: 参数错误 (比如 max_tokens 太大)
    class InvalidRequestError(message: String) : AIException(message)

    // 网络连接问题 (DNS, Timeout, Offline)
    class NetworkError(message: String = "网络连接失败，请检查网络设置", cause: Throwable? = null) : AIException(message, cause)

    // 其他未知错误
    class UnknownError(message: String, cause: Throwable? = null) : AIException(message, cause)
}