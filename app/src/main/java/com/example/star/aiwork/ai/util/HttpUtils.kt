package com.example.star.aiwork.ai.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.star.aiwork.ai.provider.CustomBody
import com.example.star.aiwork.ai.provider.CustomHeader
import com.example.star.aiwork.ai.provider.ProviderProxy
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore cancel exception
            }
        }
    }
}

fun OkHttpClient.configureClientWithProxy(proxySetting: ProviderProxy): OkHttpClient {
    return when (proxySetting) {
        is ProviderProxy.None -> this
        is ProviderProxy.Http -> {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxySetting.address, proxySetting.port))
            this.newBuilder()
                .proxy(proxy)
                // Authenticator logic could be added here if needed
                .build()
        }
    }
}

fun List<CustomHeader>.toHeaders(): Headers {
    val builder = Headers.Builder()
    forEach { builder.add(it.name, it.value) }
    return builder.build()
}

fun JsonObject.mergeCustomBody(customBody: List<CustomBody>): JsonObject {
    if (customBody.isEmpty()) return this
    return buildJsonObject {
        // Add original keys
        this@mergeCustomBody.forEach { (k, v) -> put(k, v) }
        // Add/Overwrite with custom body
        customBody.forEach {
            put(it.key, it.value)
        }
    }
}

fun JsonObject.getByKey(path: String): String {
    // Simple implementation for getting a value by dot-notation path
    // e.g. "data.total_usage"
    val keys = path.split(".")
    var current: JsonElement? = this
    for (key in keys) {
        if (current is JsonObject) {
            current = current[key]
        } else {
            return ""
        }
    }
    return current?.jsonPrimitive?.content ?: ""
}

val JsonElement.jsonPrimitiveOrNull
    get() = try {
        jsonPrimitive
    } catch (e: IllegalArgumentException) {
        null
    }
