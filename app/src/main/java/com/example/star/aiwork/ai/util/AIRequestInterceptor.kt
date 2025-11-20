package com.example.star.aiwork.ai.util

import com.example.star.aiwork.ai.util.json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset

class AIRequestInterceptor : Interceptor {
    // In a real app, these would come from FirebaseRemoteConfig or similar
    private val freeModels = listOf("Qwen/Qwen2.5-7B-Instruct", "THUDM/glm-4-9b-chat")
    // Using the key from FakeData for demonstration
    private val fallbackApiKey = "sk-kvvjdrxnhqicbrjdbvbgwuyyvstssgmeqgufhuqwpjqvjuyg"

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val host = request.url.host

        if (host == "api.siliconflow.cn") {
            request = processSiliconCloudRequest(request)
        }

        return chain.proceed(request)
    }

    private fun processSiliconCloudRequest(request: Request): Request {
        val authHeader = request.header("Authorization")
        val path = request.url.encodedPath

        // 如果没有设置api token, 填入免费api key
        // Checks if Auth header is missing, empty, or just "Bearer" / "Bearer sk-" (placeholder)
        if ((authHeader.isNullOrBlank() || authHeader.trim() == "Bearer" || authHeader.trim() == "Bearer sk-") && 
            path in listOf("/v1/chat/completions", "/v1/models")
        ) {
            val bodyJson = request.readBodyAsJson()
            val model = bodyJson?.jsonObject?.get("model")?.jsonPrimitive?.content
            
            // If model is null (maybe listing models?) or in the free list
            if (model.isNullOrEmpty() || model in freeModels) {
                return request.newBuilder()
                    .header("Authorization", "Bearer $fallbackApiKey")
                    .build()
            }
        }

        return request
    }
    
    private fun Request.readBodyAsJson(): JsonElement? {
        val contentType = body?.contentType()
        if (contentType?.type == "application" && contentType.subtype == "json") {
            // We need to clone the body because reading it consumes it
            val buffer = Buffer()
            body?.writeTo(buffer)
            
            val charset = contentType.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
            val jsonString = buffer.readString(charset)
            return try {
                json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
