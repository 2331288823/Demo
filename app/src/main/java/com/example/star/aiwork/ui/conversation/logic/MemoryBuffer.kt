package com.example.star.aiwork.ui.conversation.logic

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 记忆缓冲区
 * 
 * 用于缓存通过过滤器的消息（包含文本和 embedding），
 * 当 buffer 满了（size == 5）时，触发批量处理。
 */
data class BufferedMemoryItem(
    val text: String,
    val embedding: FloatArray
) {
    // FloatArray 需要自定义 equals 和 hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as BufferedMemoryItem
        
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

class MemoryBuffer(
    private val maxSize: Int = 5,
    private val onBufferFull: suspend (List<BufferedMemoryItem>) -> Unit
) {
    private val mutex = Mutex()
    private val buffer = mutableListOf<BufferedMemoryItem>()

    /**
     * 添加一条消息到 buffer
     * 如果 buffer 满了，会触发 onBufferFull 回调
     */
    suspend fun add(item: BufferedMemoryItem) {
        val textPreview = item.text.take(100)
        val embeddingSize = item.embedding.size
        
        val itemsToProcess = mutex.withLock {
            buffer.add(item)
            val currentSize = buffer.size
            Log.d("MemoryBuffer", "📥 [添加消息] 当前 buffer 大小: $currentSize/$maxSize")
            Log.d("MemoryBuffer", "   └─ 消息预览: $textPreview${if (item.text.length > 100) "..." else ""}")
            Log.d("MemoryBuffer", "   └─ Embedding 维度: $embeddingSize")
            
            if (currentSize >= maxSize) {
                Log.d("MemoryBuffer", "✅ [Buffer 已满] 触发批量处理，准备处理 $currentSize 条消息")
                val items = buffer.toList()
                // 记录所有待处理的消息
                items.forEachIndexed { index, bufferedItem ->
                    Log.d("MemoryBuffer", "   [$index] ${bufferedItem.text.take(80)}${if (bufferedItem.text.length > 80) "..." else ""}")
                }
                buffer.clear()
                items
            } else {
                Log.d("MemoryBuffer", "   └─ 还需 ${maxSize - currentSize} 条消息才能触发批量处理")
                null
            }
        }
        
        // 在锁外执行回调，避免阻塞
        itemsToProcess?.let {
            Log.d("MemoryBuffer", "🚀 [触发回调] 开始批量处理 ${it.size} 条消息")
            onBufferFull(it)
        }
    }

    /**
     * 获取当前 buffer 的大小
     */
    suspend fun size(): Int {
        return mutex.withLock {
            val size = buffer.size
            Log.d("MemoryBuffer", "📊 [查询大小] 当前 buffer 大小: $size/$maxSize")
            size
        }
    }

    /**
     * 清空 buffer
     */
    suspend fun clear() {
        mutex.withLock {
            val clearedCount = buffer.size
            buffer.clear()
            Log.d("MemoryBuffer", "🗑️ [清空 Buffer] 已清空 $clearedCount 条消息")
        }
    }

    /**
     * 手动触发处理（即使 buffer 未满）
     * 用于应用关闭等场景
     */
    suspend fun flush() {
        val itemsToProcess = mutex.withLock {
            if (buffer.isNotEmpty()) {
                val count = buffer.size
                Log.d("MemoryBuffer", "🔄 [手动 Flush] 触发处理，当前 buffer 有 $count 条消息（未满 $maxSize）")
                val items = buffer.toList()
                // 记录所有待处理的消息
                items.forEachIndexed { index, item ->
                    Log.d("MemoryBuffer", "   [$index] ${item.text.take(80)}${if (item.text.length > 80) "..." else ""}")
                }
                buffer.clear()
                items
            } else {
                Log.d("MemoryBuffer", "⚠️ [手动 Flush] Buffer 为空，无需处理")
                null
            }
        }
        
        // 在锁外执行回调，避免阻塞
        itemsToProcess?.let {
            Log.d("MemoryBuffer", "🚀 [触发回调] 开始批量处理 ${it.size} 条消息（手动 flush）")
            onBufferFull(it)
        }
    }
}

