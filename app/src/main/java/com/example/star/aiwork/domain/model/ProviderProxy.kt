package com.example.star.aiwork.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 提供商代理设置 (Provider Proxy)。
 * 定义连接 AI 服务时使用的网络代理配置。
 */
@Serializable
sealed class ProviderProxy {
    /**
     * 不使用代理。
     */
    @Serializable
    @SerialName("none")
    object None : ProviderProxy()

    /**
     * 使用 HTTP 代理。
     *
     * @property address 代理服务器地址。
     * @property port 端口号。
     * @property username 用户名（可选）。
     * @property password 密码（可选）。
     */
    @Serializable
    @SerialName("http")
    data class Http(
        val address: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProviderProxy()
}

/**
 * 余额查询选项 (Balance Option)。
 * 配置如何从第三方接口自动获取账户余额。
 *
 * @property enabled 是否启用余额显示。
 * @property apiPath 获取余额的 API 路径（相对于 Base URL）。
 * @property resultPath JSON 响应中余额字段的路径（例如 "data.total_usage"）。
 */
@Serializable
data class BalanceOption(
    val enabled: Boolean = false, // 是否开启余额获取功能
    val apiPath: String = "/credits", // 余额获取API路径
    val resultPath: String = "data.total_usage", // 余额获取JSON路径
)
