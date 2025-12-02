package com.example.star.aiwork.data.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import com.example.star.aiwork.domain.Provider
import com.example.star.aiwork.domain.ImageGenerationParams
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.ui.ai.ImageGenerationResult
import com.example.star.aiwork.ui.ai.MessageChunk
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.ui.ai.UIMessageChoice
import com.example.star.aiwork.infra.util.KeyRoulette
import com.example.star.aiwork.infra.util.configureClientWithProxy
import com.example.star.aiwork.infra.util.json
import com.example.star.aiwork.infra.util.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.UUID

/**
 * Google (Gemini) 提供商实现。
 *
 * 仿照 [OllamaProvider] 的实现模式，使用 OkHttp 直接调用 Google AI Studio API。
 */
class GoogleProvider(
    private val client: OkHttpClient
) : Provider<ProviderSetting.Google> {

    private val keyRoulette = KeyRoulette.default()

    /**
     * 动态列出 Google 提供的可用模型。
     * 发送 GET /v1beta/models 请求，并过滤出支持 generateContent 的模型。
     */
    override suspend fun listModels(providerSetting: ProviderSetting.Google): List<Model> =
        withContext(Dispatchers.IO) {
            val key = keyRoulette.next(providerSetting.apiKey)
            val baseUrl = providerSetting.baseUrl.trimEnd('/')
            // API: https://generativelanguage.googleapis.com/v1beta/models?key=API_KEY
            val url = "$baseUrl/models?key=$key"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.configureClientWithProxy(providerSetting.proxy)
                .newCall(request)
                .await()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                // 如果动态获取失败，回退到硬编码列表，保证基础可用性
                // 或者直接抛出异常让用户知道配置有误
                throw RuntimeException("Failed to list models: ${response.code} $errorBody")
            }

            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            
            try {
                val jsonElement = json.parseToJsonElement(bodyStr).jsonObject
                val modelsArray = jsonElement["models"]?.jsonArray ?: return@withContext emptyList()

                modelsArray.mapNotNull { modelJson ->
                    val modelObj = modelJson.jsonObject
                    // name 格式通常为 "models/gemini-1.5-flash"
                    val name = modelObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val displayName = modelObj["displayName"]?.jsonPrimitive?.contentOrNull ?: name
                    val supportedMethods = modelObj["supportedGenerationMethods"]?.jsonArray?.map { 
                        it.jsonPrimitive.content 
                    } ?: emptyList()

                    // 过滤：只保留支持 generateContent 的模型（排除 embedding 模型等）
                    if (supportedMethods.contains("generateContent")) {
                        // 去掉 "models/" 前缀，以便在调用 API 时直接使用 ID（虽然 API 接受带前缀的，但为了 UI 简洁）
                        // 注意：调用 API 时如果只传 ID，Google 客户端库通常会自动处理，
                        // 但直接 HTTP 调用时，有些端点需要 "models/" 前缀，有些不需要。
                        // 我们的 streamText 实现中是拼接 "$baseUrl/models/$modelId..."
                        // 如果 modelId 已经是 "models/gemini-pro"，拼接后变成 ".../models/models/gemini-pro" 可能会错
                        // 所以这里我们剥离前缀，确保 modelId 是纯 ID (如 "gemini-1.5-flash")
                        val cleanId = if (name.startsWith("models/")) name.substring(7) else name
                        
                        Model(
                            modelId = cleanId,
                            displayName = displayName
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 解析失败时返回空，或回退
                emptyList()
            }
        }

    /**
     * 非流式生成。
     * 为了简化实现，这里复用 streamText 并收集结果，这与 Ollama 的独立实现略有不同，但结果结构一致。
     */
    override suspend fun generateText(
        providerSetting: ProviderSetting.Google,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        val sb = StringBuilder()
        var finishReason: String? = null
        
        // 收集流式结果
        streamText(providerSetting, messages, params).collect { chunk ->
            chunk.choices.firstOrNull()?.delta?.parts?.forEach { part ->
                if (part is UIMessagePart.Text) {
                    sb.append(part.text)
                }
            }
            val reason = chunk.choices.firstOrNull()?.finishReason
            if (reason != null) {
                finishReason = reason
            }
        }
        
        val content = sb.toString()
        
        // 构造完整的 MessageChunk，仿照 OllamaProvider.generateText 的返回结构
        return MessageChunk(
            id = UUID.randomUUID().toString(),
            model = params.model.modelId,
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(content))
                    ),
                    finishReason = finishReason ?: "stop"
                )
            )
        )
    }

    /**
     * 流式生成。
     * 仿照 OllamaProvider.streamText，但在解析时适配 Google 的 SSE 格式。
     */
    override suspend fun streamText(
        providerSetting: ProviderSetting.Google,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = flow {
        val key = keyRoulette.next(providerSetting.apiKey)
        val modelId = params.model.modelId

        // API 端点: streamGenerateContent (支持 SSE)
        val baseUrl = providerSetting.baseUrl.trimEnd('/')
        val url = "$baseUrl/models/$modelId:streamGenerateContent?alt=sse&key=$key"

        val requestBody = buildGeminiRequestBody(messages, params)

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.configureClientWithProxy(providerSetting.proxy)
            .newCall(request)
            .await()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            error("Google API error: ${response.code} $errorBody")
        }

        val source = response.body?.source() ?: error("Empty response body")
        val reader = BufferedReader(source.inputStream().reader())

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                // Google SSE 格式以 "data: " 开头
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break
                    
                    try {
                        val jsonElement = json.parseToJsonElement(data).jsonObject
                        
                        // 解析 Google 响应结构
                        val candidates = jsonElement["candidates"]?.jsonArray
                        val candidate = candidates?.firstOrNull()?.jsonObject
                        
                        // 提取文本内容
                        val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                        val text = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        
                        // 提取结束原因
                        val finishReason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
                        
                        if (!text.isNullOrEmpty() || finishReason != null) {
                            // 构造 delta 消息
                            val deltaMessage = UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = if (!text.isNullOrEmpty()) listOf(UIMessagePart.Text(text)) else emptyList()
                            )
                            
                            // 发射 Chunk，结构与 OllamaProvider.streamText 一致
                            emit(MessageChunk(
                                id = UUID.randomUUID().toString(),
                                model = modelId,
                                choices = listOf(
                                    UIMessageChoice(
                                        index = 0,
                                        delta = deltaMessage,
                                        message = null,
                                        finishReason = if (finishReason != null && finishReason != "STOP") finishReason else null
                                    )
                                )
                            ))
                        }
                    } catch (e: Exception) {
                        // 忽略部分解析错误
                    }
                }
                line = reader.readLine()
            }
        } finally {
            reader.close()
            response.close()
        }
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult {
        throw NotImplementedError("Image generation not implemented for Google provider")
    }

    private fun buildGeminiRequestBody(
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): String {
        return buildJsonObject {
            put("contents", buildJsonArray {
                messages.forEach { msg ->
                    add(buildJsonObject {
                        // Google 角色映射: user -> user, assistant -> model
                        put("role", if (msg.role == MessageRole.USER) "user" else "model")
                        put("parts", buildJsonArray {
                            msg.parts.forEach { part ->
                                when (part) {
                                    is UIMessagePart.Text -> {
                                        add(buildJsonObject {
                                            put("text", part.text)
                                        })
                                    }
                                    is UIMessagePart.Image -> {
                                        if (part.url.startsWith("data:")) {
                                            val base64Data = part.url.substringAfter("base64,")
                                            val mimeType = part.url.substringAfter("data:").substringBefore(";")
                                            add(buildJsonObject {
                                                put("inline_data", buildJsonObject {
                                                    put("mime_type", mimeType)
                                                    put("data", base64Data)
                                                })
                                            })
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        })
                    })
                }
            })
            
            put("generationConfig", buildJsonObject {
                params.temperature?.let { put("temperature", it) }
                params.maxTokens?.let { put("maxOutputTokens", it) }
                params.topP?.let { put("topP", it) }
            })
        }.toString()
    }
}
