package com.example.star.aiwork.data.repository

import com.example.star.aiwork.domain.model.embedding.SessionEmbedding
import kotlinx.coroutines.flow.Flow

/**
 * 会话向量嵌入仓库接口
 */
interface SessionEmbeddingRepository {
    /**
     * 保存会话向量
     */
    suspend fun saveSessionEmbedding(embedding: SessionEmbedding)

    /**
     * 根据会话 ID 获取所有向量
     */
    suspend fun getEmbeddingsBySession(sessionId: String): List<SessionEmbedding>

    /**
     * 观察指定会话的向量列表
     */
    fun observeEmbeddingsBySession(sessionId: String): Flow<List<SessionEmbedding>>

    /**
     * 根据会话 ID 和文本获取向量
     */
    suspend fun getEmbeddingBySessionAndText(sessionId: String, text: String): SessionEmbedding?

    /**
     * 获取所有会话向量
     */
    suspend fun getAllSessionEmbeddings(): List<SessionEmbedding>

    /**
     * 删除指定会话的所有向量
     */
    suspend fun deleteEmbeddingsBySession(sessionId: String)
}

