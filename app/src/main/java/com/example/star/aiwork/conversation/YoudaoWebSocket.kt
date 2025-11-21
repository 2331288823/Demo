package com.example.star.aiwork.conversation

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class YoudaoWebSocket(private val listener: TranscriptionListener) {

    private var webSocket: WebSocket? = null
    private var lastPartialResult: String = ""
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    // TODO: Replace with real App Key and Secret
    private val APP_KEY = "1fa9647ca43dd17a"
    private val APP_SECRET = "adcF7pXU5MK2yfzVRN5OfJSSUVsIpLEg"

    interface TranscriptionListener {
        fun onResult(text: String)
        fun onError(t: Throwable)
    }

    fun connect() {
        val nonce = UUID.randomUUID().toString()
        val curTime = (System.currentTimeMillis() / 1000).toString()
        val sign = calculateSign(APP_KEY, nonce, curTime, APP_SECRET)

        // Youdao Stream Speech Translation URL
        // Doc: wss://openapi.youdao.com/stream_speech_trans
        val url = "wss://openapi.youdao.com/stream_speech_trans?appKey=$APP_KEY&salt=$nonce&curtime=$curTime&sign=$sign&signType=v4&from=zh-CHS&to=en&rate=16000&format=wav&channel=1&version=v1&transPattern=stream"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closing: $reason")
                // If we have a pending partial result when closing, send it now
                if (lastPartialResult.isNotEmpty()) {
                    Log.d(TAG, "Sending last partial result on closing: $lastPartialResult")
                    listener.onResult(lastPartialResult)
                    lastPartialResult = ""
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${response?.message}", t)
                listener.onError(t)
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val jsonObject = JSONObject(text)
            val action = jsonObject.optString("action")
            if (action == "recognition") {
                val sb = StringBuilder()
                
                // Handle result as Object (as seen in logs)
                val resultObj = jsonObject.optJSONObject("result")
                if (resultObj != null) {
                    val currentText = extractText(resultObj)
                    // Only append if it's a final result (partial is false or missing)
                    if (!resultObj.optBoolean("partial")) {
                        sb.append(currentText)
                        lastPartialResult = "" // Clear partial since we have a final
                    } else {
                        lastPartialResult = currentText // Store partial
                        Log.d(TAG, "Skipping partial result: $currentText")
                    }
                } else {
                    // Handle result as Array (fallback/legacy)
                    val resultArray = jsonObject.optJSONArray("result")
                    if (resultArray != null && resultArray.length() > 0) {
                        for (i in 0 until resultArray.length()) {
                            val item = resultArray.getJSONObject(i)
                            val currentText = extractText(item)
                            if (!item.optBoolean("partial")) {
                                sb.append(currentText)
                                lastPartialResult = ""
                            } else {
                                lastPartialResult = currentText
                            }
                        }
                    }
                }
                
                val resultText = sb.toString()
                if (resultText.isNotEmpty()) {
                    listener.onResult(resultText)
                }
            } else if (action == "error") {
                val code = jsonObject.optString("errorCode")
                listener.onError(RuntimeException("API Error: $code - $text"))
            }
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    private fun extractText(json: JSONObject): String {
        // Prioritize 'context' (recognized text) as per user request for "Chinese recognition"
        var text = json.optString("context")
        if (text.isNotEmpty()) return text

        // Fallback to 'txt'
        text = json.optString("txt")
        if (text.isNotEmpty()) return text
        
        // Fallback to 'tranContent' (translation)
        text = json.optString("tranContent")
        if (text.isNotEmpty()) return text
        
        // Fallback to 'trans'
        text = json.optString("trans")
        if (text.isNotEmpty()) return text
        
        return ""
    }

    fun sendAudio(data: ByteArray, len: Int) {
        webSocket?.send(data.toByteString(0, len))
    }

    fun close() {
        webSocket?.close(1000, "User closed")
        webSocket = null
    }

    private fun calculateSign(appKey: String, salt: String, curTime: String, appSecret: String): String {
        val str = appKey + salt + curTime + appSecret
        return sha256(str)
    }

    private fun sha256(str: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(str.toByteArray())
        val hexString = StringBuilder()
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    companion object {
        private const val TAG = "YoudaoWebSocket"
    }
}
