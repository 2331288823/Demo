package com.example.star.aiwork.infra.network

import android.util.Log
import com.example.star.aiwork.infra.util.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.EOFException
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * SSE 客户端，负责长连接读取与统一异常处理。
 */
class SseClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val charset: Charset = Charsets.UTF_8
) {

    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val activeCancelledTasks = ConcurrentHashMap<String, Boolean>()

    /**
     * 根据请求创建 SSE 流。
     */
    fun createStream(
        request: Request,
        taskId: String,
        clientOverride: OkHttpClient? = null
    ): Flow<String> = flow {
        val callClient = clientOverride ?: okHttpClient
        val call = callClient.newCall(request)
        activeCalls[taskId]?.cancel()
        activeCalls[taskId] = call

        try {
            val response = call.await()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string().orEmpty()
                    throw NetworkException.HttpException(resp.code, errorBody)
                }
                val body = resp.body ?: throw NetworkException.UnknownException("SSE 响应体为空")
                body.source().inputStream().use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, charset))
                    reader.useLines { sequence ->
                        for (line in sequence) {
                            coroutineContext.ensureActive()
                            val payload = line.parseSseData() ?: continue
                            emit(payload)
                        }
                    }
                }
            }
        } catch (io: IOException) {
            // 检查是否是主动取消导致的异常
            if (activeCancelledTasks.remove(taskId) == true) {
                throw CancellationException("SSE stream was cancelled", io)
            }
            throw mapIOException(io)
        } catch (ce: CancellationException) {
            // 重新抛出取消异常，不转换为NetworkException
            throw ce
        } finally {
            activeCalls.remove(taskId)
            activeCancelledTasks.remove(taskId)  // 清理标记
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 取消指定任务。
     */
    fun cancel(taskId: String) {
        try {
            activeCancelledTasks[taskId] = true  // 标记为主动取消
            activeCalls.remove(taskId)?.cancel()
        } catch (e: Exception) {
            // 取消操作应该静默处理，即使失败也不应该抛出异常
            // Call.cancel() 通常不会抛出异常，但在某些边缘情况下可能会
            Log.d("SseClient", "Cancel call failed for taskId: $taskId", e)
        }
    }

    /**
     * 取消所有活动任务。
     */
    fun cancelAll() {
        activeCalls.entries.forEach { (_, call) ->
            call.cancel()
        }
        activeCalls.clear()
    }

    private fun String.parseSseData(): String? {
        // 忽略心跳或注释
        if (isBlank() || startsWith(":")) return null
        return if (startsWith("data:")) {
            substringAfter("data:").trimStart()
        } else {
            this
        }
    }
}


private fun mapIOException(e: IOException): NetworkException {
    // 检查是否是 StreamResetException（OkHttp 内部类，表示连接被重置）
    val className = e.javaClass.name
    if (className.contains("StreamResetException", ignoreCase = true)) {
        return NetworkException.ConnectionException(message = "连接被重置", cause = e)
    }

    return when (e) {
        is SocketTimeoutException -> NetworkException.TimeoutException(cause = e)
        is UnknownHostException -> NetworkException.ConnectionException(cause = e)
        is ConnectException -> NetworkException.ConnectionException(cause = e)
        is SSLHandshakeException -> NetworkException.ConnectionException(message = "SSL 握手失败，连接已关闭", cause = e)
        is SSLException -> NetworkException.ConnectionException(message = "SSL 连接失败", cause = e)
        is EOFException -> {
            // EOFException 通常表示连接被意外关闭（如断网、服务器关闭连接等）
            NetworkException.ConnectionException(message = "连接已关闭", cause = e)
        }
        else -> NetworkException.UnknownException(message = "SSE 读取失败", cause = e)
    }
}

