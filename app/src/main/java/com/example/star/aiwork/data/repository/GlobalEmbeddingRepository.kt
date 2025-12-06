package com.example.star.aiwork.data.repository

import com.example.star.aiwork.domain.model.embedding.GlobalEmbedding

/**
 * 全局向量嵌入仓库接口
 */
interface GlobalEmbeddingRepository {
    /**
     * 保存全局向量
     */
    suspend fun saveGlobalEmbedding(embedding: GlobalEmbedding)

    /**
     * 根据 ID 获取全局向量
     */
    suspend fun getGlobalEmbedding(id: Int): GlobalEmbedding?

    /**
     * 根据文本获取全局向量
     */
    suspend fun getGlobalEmbeddingByText(text: String): GlobalEmbedding?

    /**
     * 获取所有全局向量
     */
    suspend fun getAllGlobalEmbeddings(): List<GlobalEmbedding>
}

