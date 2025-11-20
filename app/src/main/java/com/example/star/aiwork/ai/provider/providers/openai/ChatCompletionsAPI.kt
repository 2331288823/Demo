package com.example.star.aiwork.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.star.aiwork.ai.core.MessageRole
import com.example.star.aiwork.ai.provider.CustomBody
import com.example.star.aiwork.ai.provider.ProviderSetting
import com.example.star.aiwork.ai.provider.TextGenerationParams
import com.example.star.aiwork.ai.ui.MessageChunk
import com.example.star.aiwork.ai.ui.UIMessage
import com.example.star.aiwork.ai.ui.UIMessagePart
import com.example.star.aiwork.ai.util.KeyRoulette
import com.example.star.aiwork.ai.util.await
import com.example.star.aiwork.ai.util.configureClientWithProxy
import com.example.star.aiwork.ai.util.json
import com.example.star.aiwork.ai.util.mergeCustomBody
import com.example.star.aiwork.ai.util.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader

class ChatCompletionsAPI(
    private val client: OkHttpClient,
    private val keyRoulette: KeyRoulette
) {

    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        val request = buildRequest(providerSetting, messages, params, stream = false)
        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to generate text: ${response.code} ${response.body?.string()}")
        }
        val bodyStr = response.body?.string() ?: error("Empty response body")
        
        // Decode into OpenAIChunk DTO first, then convert to MessageChunk
        val openAIChunk = json.decodeFromString<OpenAIChunk>(bodyStr)
        return openAIChunk.toMessageChunk()
    }

    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = flow {
        val request = buildRequest(providerSetting, messages, params, stream = true)
        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to stream text: ${response.code} ${response.body?.string()}")
        }

        val source = response.body?.source() ?: error("Empty response body")
        val reader = BufferedReader(source.inputStream().reader())

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") break
                    try {
                        // Decode into OpenAIChunk DTO first
                        val chunk = json.decodeFromString<OpenAIChunk>(data)
                        emit(chunk.toMessageChunk())
                    } catch (e: Exception) {
                        // Ignore malformed chunks or keepalive
                        // e.printStackTrace() // Uncomment for debug
                    }
                }
                line = reader.readLine()
            }
        } finally {
            reader.close()
            response.close()
        }
    }

    private fun buildRequest(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean
    ): Request {
        val key = keyRoulette.next(providerSetting.apiKey)
        val url = "${providerSetting.baseUrl}${providerSetting.chatCompletionsPath}"

        val messagesJson = buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", msg.role.name.lowercase())
                    // Simplified content handling for now. 
                    // In production, handle multimodal content (images) here.
                    put("content", msg.toText())
                })
            }
        }

        val jsonBody = buildJsonObject {
            put("model", params.model.modelId)
            put("messages", messagesJson)
            put("stream", stream)
            if (params.temperature != null) put("temperature", params.temperature)
            if (params.topP != null) put("top_p", params.topP)
            if (params.maxTokens != null) put("max_tokens", params.maxTokens)
        }.mergeCustomBody(params.customBody)

        val requestBody = json.encodeToString(jsonBody)
            .toRequestBody("application/json".toMediaType())

        return Request.Builder()
            .url(url)
            .headers(params.customHeaders.toHeaders())
            .addHeader("Authorization", "Bearer $key")
            .post(requestBody)
            .build()
    }
}
