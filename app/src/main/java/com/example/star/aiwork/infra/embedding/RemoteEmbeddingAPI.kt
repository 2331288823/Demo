package com.example.star.aiwork.infra.embedding

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.star.aiwork.infra.util.json
import com.example.star.aiwork.infra.util.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI Embeddings API 响应数据类。
 */
@Serializable
data class EmbeddingResponse(
    @SerialName("object") val objectType: String,
    val data: List<EmbeddingData>,
    val model: String,
    val usage: EmbeddingUsage?
)

@Serializable
data class EmbeddingData(
    @SerialName("object") val objectType: String,
    val index: Int,
    val embedding: List<Float>
)

@Serializable
data class EmbeddingUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

/**
 * 远程 Embedding API 服务类。
 * 
 * 用于调用 OpenAI 兼容的 embeddings API 来生成文本的向量嵌入。
 * 
 * @param client OkHttpClient 实例，用于发送网络请求
 * @param baseUrl API 基础 URL，默认为 OpenAI 官方 API 地址
 * 
 * 使用示例：
 * ```
 * val api = RemoteEmbeddingAPI(okHttpClient)
 * // 在协程中调用
 * val embedding = withContext(Dispatchers.IO) {
 *     api.embed(
 *         text = "这是一个测试文本",
 *         apiKey = "sk-your-api-key",
 *         model = "text-embedding-3-large"
 *     )
 * }
 * ```
 */
class RemoteEmbeddingAPI(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
) {
    
    private val TAG = "RemoteEmbeddingAPI"
    
    /**
     * 生成文本的向量嵌入。
     * 
     * 发送 POST 请求到 /embeddings 端点，并返回向量数组。
     * 
     * @param text 输入的文本
     * @param apiKey OpenAI API Key
     * @param model 使用的模型，默认为 "text-embedding-3-large"
     * @return 向量嵌入数组，如果生成失败则返回 null
     */
    suspend fun embed(
        text: String,
        apiKey: String = "sk-2bdb0915b50b4b8ab9525bfd122ed3a0",
        model: String = "text-embedding-v3"
    ): FloatArray? = withContext(Dispatchers.IO) {
        Log.d(TAG, "[开始] 生成远程 embedding，文本长度: ${text.length}, 模型: $model")
        
        // 输入验证
        if (text.isBlank()) {
            Log.w(TAG, "[输入验证] 文本为空，返回 null")
            return@withContext null
        }
        
        if (apiKey.isBlank()) {
            Log.e(TAG, "[输入验证] API Key 为空")
            return@withContext null
        }
        
        try {
            // 构建请求 URL
            val url = "$baseUrl/embeddings"
            Log.d(TAG, "[请求构建] URL: $url")
            Log.d(TAG, "[请求构建] Base URL: $baseUrl")
            
            // 构建请求体
            val requestBodyJson = buildJsonObject {
                put("model", model)
                put("input", text)
            }
            val requestBodyStr = json.encodeToString(requestBodyJson)
            val requestBody = requestBodyStr.toRequestBody("application/json".toMediaType())
            Log.d(TAG, "[请求构建] 请求体大小: ${requestBodyStr.length} 字符")
            
            // 构建请求
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${apiKey}")
                .post(requestBody)
                .build()
            
            Log.d(TAG, "[发送请求] 开始发送 HTTP 请求")
            val requestStartTime = System.currentTimeMillis()
            
            // 发送请求
            val response = client.newCall(request).await()
            val requestDuration = System.currentTimeMillis() - requestStartTime
            Log.d(TAG, "[响应接收] HTTP ${response.code}, 耗时: ${requestDuration}ms")
            
            // 检查响应状态
            if (!response.isSuccessful) {
                val errorBody = try {
                    response.body?.string() ?: "响应体为空"
                } catch (e: Exception) {
                    "无法读取错误响应体: ${e.message}"
                }
                Log.e(TAG, "[请求失败] HTTP ${response.code}: $errorBody")
                Log.e(TAG, "[请求失败] 响应头: ${response.headers}")
                return@withContext null
            }
            
            // 解析响应
            val responseBody = try {
                response.body?.string()
            } catch (e: Exception) {
                Log.e(TAG, "[响应解析] 读取响应体失败: ${e.message}", e)
                null
            }
            
            if (responseBody.isNullOrBlank()) {
                Log.e(TAG, "[响应解析] 响应体为空")
                return@withContext null
            }
            
            Log.d(TAG, "[响应解析] 响应体大小: ${responseBody.length} 字符")
            Log.d(TAG, "[响应解析] 响应体预览: ${responseBody.take(200)}${if (responseBody.length > 200) "..." else ""}")
            
            val embeddingResponse = try {
                json.decodeFromString<EmbeddingResponse>(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "[响应解析] JSON 解析失败: ${e.message}", e)
                Log.e(TAG, "[响应解析] 原始响应: $responseBody")
                return@withContext null
            }
            
            // 检查是否有数据
            if (embeddingResponse.data.isEmpty()) {
                Log.e(TAG, "[响应解析] 响应中没有 embedding 数据")
                return@withContext null
            }
            
            // 获取第一个 embedding（通常只有一个）
            val embedding = embeddingResponse.data[0].embedding
            val embeddingArray = embedding.toFloatArray()
            
            Log.d(TAG, "[完成] 成功生成 embedding，维度: ${embeddingArray.size}")
            embeddingArray
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "[网络错误] 无法解析主机名: ${e.message}", e)
            Log.e(TAG, "[网络错误] Base URL: $baseUrl")
            null
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "[网络错误] 连接超时: ${e.message}", e)
            null
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "[网络错误] 连接失败: ${e.message}", e)
            Log.e(TAG, "[网络错误] Base URL: $baseUrl")
            null
        } catch (e: javax.net.ssl.SSLException) {
            Log.e(TAG, "[网络错误] SSL 错误: ${e.message}", e)
            null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "[网络错误] IO 异常: ${e.message}", e)
            e.printStackTrace()
            null
        } catch (e: Exception) {
            Log.e(TAG, "[错误] 生成 embedding 失败: ${e.javaClass.simpleName}: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
}

