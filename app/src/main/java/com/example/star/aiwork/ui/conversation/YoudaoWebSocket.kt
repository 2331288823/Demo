/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork.ui.conversation

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 有道智云实时语音翻译 WebSocket 客户端。
 *
 * 负责与有道语音识别服务建立连接，发送音频数据，并接收识别结果。
 *
 * @param listener 识别结果回调监听器。
 */
class YoudaoWebSocket(private val listener: TranscriptionListener) {

    // 有道 API 配置
    // 注意：在生产环境中，应将这些敏感信息存储在安全的地方（如 BuildConfig 或加密存储），不应硬编码。
    private val appKey = "1fa9647ca43dd17a"
    private val appSecret = "adcF7pXU5MK2yfzVRN5OfJSSUVsIpLEg"
    private val url = "wss://openapi.youdao.com/stream_asropenapi"

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 转录监听器接口。
     */
    interface TranscriptionListener {
        /**
         * 接收到识别结果。
         * @param text 识别出的文本。
         * @param isFinal 是否为最终结果（如果为 false，则为中间部分结果）。
         */
        fun onResult(text: String, isFinal: Boolean)
        
        /**
         * 发生错误。
         */
        fun onError(t: Throwable)
    }

    /**
     * 连接 WebSocket。
     * 生成签名并建立连接。
     */
    fun connect() {
        val salt = UUID.randomUUID().toString()
        val curTime = (System.currentTimeMillis() / 1000).toString()
        val signStr = appKey + salt + curTime + appSecret
        val sign = sha256(signStr)

        val requestUrl = "$url?appKey=$appKey&salt=$salt&curtime=$curTime&sign=$sign&signType=v4&langType=zh-CHS&rate=16000&format=wav&channel=1&version=v1"

        val request = Request.Builder()
            .url(requestUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("YoudaoWebSocket", "Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    // 解析 JSON 响应
                    val jsonElement = json.parseToJsonElement(text)
                    // 安全转换为 JsonObject，避免异常
                    val jsonObject = jsonElement as? JsonObject
                    
                    if (jsonObject == null) {
                        Log.w("YoudaoWebSocket", "Received non-object JSON: $text")
                        return
                    }
                    
                    // 安全获取 errorCode
                    // 使用 (element as? JsonPrimitive) 避免 "is not a JsonPrimitive" 异常
                    val errorCodeElement = jsonObject["errorCode"]
                    val errorCode = (errorCodeElement as? JsonPrimitive)?.contentOrNull
                    
                    if (errorCode == "0") {
                        // 解析结果: {"result":[{"st":{"sentence":"...", "partial":true}, "seg_id":...}]}
                        val resultArr = jsonObject["result"] as? JsonArray
                        val resultObj = resultArr?.getOrNull(0) as? JsonObject
                        val st = resultObj?.get("st") as? JsonObject
                        
                        val sentenceElement = st?.get("sentence")
                        val sentence = (sentenceElement as? JsonPrimitive)?.contentOrNull
                        
                        val partialElement = st?.get("partial")
                        val partial = (partialElement as? JsonPrimitive)?.booleanOrNull ?: true
                        
                        if (!sentence.isNullOrEmpty()) {
                            listener.onResult(sentence, isFinal = !partial) 
                        }
                    } else if (errorCode != null) {
                        Log.e("YoudaoWebSocket", "Error from server: $errorCode")
                    } else {
                        // 如果没有 errorCode 字段，尝试直接解析 result (兼容性处理)
                         val resultArr = jsonObject["result"] as? JsonArray
                         val resultObj = resultArr?.getOrNull(0) as? JsonObject
                         val st = resultObj?.get("st") as? JsonObject
                         val sentence = (st?.get("sentence") as? JsonPrimitive)?.contentOrNull
                         
                         if (!sentence.isNullOrEmpty()) {
                             val partial = (st?.get("partial") as? JsonPrimitive)?.booleanOrNull ?: true
                             listener.onResult(sentence, isFinal = !partial) 
                         }
                    }
                } catch (e: Exception) {
                    Log.e("YoudaoWebSocket", "Json parse error: ${e.message}. Raw text: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("YoudaoWebSocket", "Closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("YoudaoWebSocket", "Failure", t)
                listener.onError(t)
            }
        })
    }

    /**
     * 发送音频数据。
     *
     * @param data 音频 PCM 数据。
     * @param len 数据长度。
     */
    fun sendAudio(data: ByteArray, len: Int) {
        if (len > 0) {
            val byteString = data.copyOfRange(0, len).toByteString()
            webSocket?.send(byteString)
        }
    }

    /**
     * 关闭连接。
     * 发送结束帧（如果有协议规定）并关闭 WebSocket。
     */
    fun close() {
        // 有道协议建议发送特定的结束帧 "{\"end\": \"true\"}" ? 
        // 文档不一，这里直接关闭连接
        webSocket?.close(1000, "User stopped")
        webSocket = null
    }

    /**
     * SHA-256 签名计算。
     */
    private fun sha256(str: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(str.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
