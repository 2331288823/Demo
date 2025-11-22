package com.example.star.aiwork.domain.model

import kotlinx.serialization.Serializable

/**
 * 自定义 HTTP 头部。
 * 用于向 API 请求中添加额外的 Header。
 *
 * @property name Header 名称。
 * @property value Header 值。
 */
@Serializable
data class CustomHeader(
    val name: String,
    val value: String
)
