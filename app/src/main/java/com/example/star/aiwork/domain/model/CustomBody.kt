package com.example.star.aiwork.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 自定义 HTTP Body 参数。
 * 用于向 API 请求体中注入额外的 JSON 字段。
 *
 * @property key JSON 键。
 * @property value JSON 值 (支持复杂对象)。
 */
@Serializable
data class CustomBody(
    val key: String,
    val value: JsonElement
)
