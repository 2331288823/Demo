package com.example.star.aiwork.ui.conversation.logic

import android.util.Log
import com.example.star.aiwork.domain.usecase.embedding.ComputeEmbeddingUseCase
import com.example.star.aiwork.domain.usecase.embedding.SaveEmbeddingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 记忆触发过滤器
 * 
 * 检测用户输入中的记忆触发词和模式，当匹配时添加到 buffer 中。
 * buffer 满了之后会通过 FilterMemoryMessagesUseCase 进行批量判断并保存。
 */
class MemoryTriggerFilter(
    private val computeEmbeddingUseCase: ComputeEmbeddingUseCase?,
    private val saveEmbeddingUseCase: SaveEmbeddingUseCase?,
    private val memoryBuffer: MemoryBuffer?
) {
    
    companion object {
        /**
         * 显式触发词列表
         */
        private val EXPLICIT_TRIGGERS = listOf(
            "记住", "帮我记", "加入记忆", "牢记",
            "以后你都", "永远记", "保存到记忆"
        )

        /**
         * 身份模式（正则表达式）
         */
        private val IDENTITY_PATTERNS = listOf(
            Regex("我叫(.+?)"),
            Regex("我是(.+?)"),
            Regex("我住在(.+?)"),
            Regex("我来自(.+?)"),
            Regex("我的职业是(.+?)")
        )

        /**
         * 偏好模式（正则表达式）
         */
        private val PREFERENCE_PATTERNS = listOf(
            Regex("我喜欢(.+?)"),
            Regex("i like(.+?)"),
            Regex("我更喜欢(.+?)"),
            Regex("我希望你(.+?)"),
            Regex("以后请你(.+?)"),
            Regex("你以后回答我(.+?)")
        )

        /**
         * 长期目标模式（正则表达式）
         */
        private val LONG_TERM_GOALS = listOf(
            Regex("我想在未来(.+?)"),
            Regex("我接下来(.+?)"),
            Regex("我计划(.+?)"),
            Regex("我打算(.+?)"),
            Regex("我的目标是(.+?)")
        )
    }

    /**
     * 检查输入文本是否匹配任何记忆触发模式
     * 
     * @param text 用户输入的文本
     * @return 如果匹配则返回 true，否则返回 false
     */
    fun shouldSaveAsMemory(text: String): Boolean {
        if (text.isBlank()) {
            Log.d("MemoryTriggerFilter", "🔍 [过滤检查] 文本为空，跳过")
            return false
        }
        
        val trimmedText = text.trim()
        val textPreview = trimmedText.take(100)
        
        // 检查显式触发词
        val explicitMatch = EXPLICIT_TRIGGERS.firstOrNull { trigger -> trimmedText.contains(trigger) }
        if (explicitMatch != null) {
            Log.d("MemoryTriggerFilter", "✅ [过滤检查] 匹配显式触发词: \"$explicitMatch\"")
            Log.d("MemoryTriggerFilter", "   └─ 文本: $textPreview${if (trimmedText.length > 100) "..." else ""}")
            return true
        }
        
        // 检查身份模式
        val identityMatch = IDENTITY_PATTERNS.firstOrNull { pattern -> pattern.containsMatchIn(trimmedText) }
        if (identityMatch != null) {
            Log.d("MemoryTriggerFilter", "✅ [过滤检查] 匹配身份模式: ${identityMatch.pattern}")
            Log.d("MemoryTriggerFilter", "   └─ 文本: $textPreview${if (trimmedText.length > 100) "..." else ""}")
            return true
        }
        
        // 检查偏好模式
        val preferenceMatch = PREFERENCE_PATTERNS.firstOrNull { pattern -> pattern.containsMatchIn(trimmedText) }
        if (preferenceMatch != null) {
            Log.d("MemoryTriggerFilter", "✅ [过滤检查] 匹配偏好模式: ${preferenceMatch.pattern}")
            Log.d("MemoryTriggerFilter", "   └─ 文本: $textPreview${if (trimmedText.length > 100) "..." else ""}")
            return true
        }
        
        // 检查长期目标模式
        val goalMatch = LONG_TERM_GOALS.firstOrNull { pattern -> pattern.containsMatchIn(trimmedText) }
        if (goalMatch != null) {
            Log.d("MemoryTriggerFilter", "✅ [过滤检查] 匹配长期目标模式: ${goalMatch.pattern}")
            Log.d("MemoryTriggerFilter", "   └─ 文本: $textPreview${if (trimmedText.length > 100) "..." else ""}")
            return true
        }
        
        Log.d("MemoryTriggerFilter", "❌ [过滤检查] 未匹配任何模式")
        Log.d("MemoryTriggerFilter", "   └─ 文本: $textPreview${if (trimmedText.length > 100) "..." else ""}")
        return false
    }

    /**
     * 处理记忆保存
     * 如果输入匹配触发模式，则计算嵌入向量并保存
     * 
     * @param text 用户输入的文本
     */
    suspend fun processMemoryIfNeeded(text: String) {
        if (!shouldSaveAsMemory(text)) {
            return
        }
        
        // 如果用例未提供，则跳过
        if (computeEmbeddingUseCase == null || saveEmbeddingUseCase == null) {
            return
        }
        
        try {
            // 在后台线程执行
            withContext(Dispatchers.IO) {
                // 计算嵌入向量
                val embedding = computeEmbeddingUseCase(text)
                
                if (embedding != null) {
                    saveMemoryWithEmbedding(text, embedding)
                }
            }
        } catch (e: Exception) {
            // 静默处理错误，不影响正常消息流程
            android.util.Log.e("MemoryTriggerFilter", "Failed to save memory: ${e.message}", e)
        }
    }

    /**
     * 使用已计算的嵌入向量处理记忆
     * 如果输入匹配触发模式，则添加到 buffer 中，等待批量处理
     * 
     * @param text 用户输入的文本
     * @param embedding 已计算的嵌入向量
     */
    suspend fun processMemoryIfNeededWithEmbedding(text: String, embedding: FloatArray) {
        Log.d("MemoryTriggerFilter", "🔍 [处理记忆] 开始检查消息是否需要保存")
        Log.d("MemoryTriggerFilter", "   └─ 文本长度: ${text.length}, Embedding 维度: ${embedding.size}")
        
        if (!shouldSaveAsMemory(text)) {
            Log.d("MemoryTriggerFilter", "⏭️ [处理记忆] 未通过过滤器，跳过")
            return
        }
        
        // 如果 buffer 未提供，则跳过
        if (memoryBuffer == null) {
            Log.w("MemoryTriggerFilter", "⚠️ [处理记忆] MemoryBuffer 未提供，无法添加到 buffer")
            return
        }
        
        try {
            // 在后台线程异步执行，不阻塞消息发送
            withContext(Dispatchers.IO) {
                Log.d("MemoryTriggerFilter", "📦 [处理记忆] 准备添加到 buffer")
                val item = BufferedMemoryItem(text, embedding)
                memoryBuffer.add(item)
                Log.d("MemoryTriggerFilter", "✅ [处理记忆] 消息已成功添加到 buffer")
            }
        } catch (e: Exception) {
            // 静默处理错误，不影响正常消息流程
            Log.e("MemoryTriggerFilter", "❌ [处理记忆] 添加到 buffer 失败: ${e.message}", e)
        }
    }

    /**
     * 直接保存记忆（用于批量处理后的保存）
     */
    suspend fun saveMemoryWithEmbedding(text: String, embedding: FloatArray) {
        Log.d("MemoryTriggerFilter", "💾 [保存记忆] 开始保存到数据库")
        Log.d("MemoryTriggerFilter", "   └─ 文本: ${text.take(80)}${if (text.length > 80) "..." else ""}")
        Log.d("MemoryTriggerFilter", "   └─ Embedding 维度: ${embedding.size}")
        
        if (saveEmbeddingUseCase == null) {
            Log.w("MemoryTriggerFilter", "⚠️ [保存记忆] SaveEmbeddingUseCase 未提供，无法保存")
            return
        }
        
        try {
            // 创建 Embedding 对象并保存
            val embeddingModel = com.example.star.aiwork.domain.model.embedding.Embedding(
                id = 0, // 数据库会自动生成
                text = text,
                embedding = embedding
            )
            
            saveEmbeddingUseCase(embeddingModel)
            Log.d("MemoryTriggerFilter", "✅ [保存记忆] 已成功保存到数据库")
        } catch (e: Exception) {
            Log.e("MemoryTriggerFilter", "❌ [保存记忆] 保存失败: ${e.message}", e)
            throw e
        }
    }
}

