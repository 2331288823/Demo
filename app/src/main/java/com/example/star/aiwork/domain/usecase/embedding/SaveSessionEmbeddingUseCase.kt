package com.example.star.aiwork.domain.usecase.embedding

import com.example.star.aiwork.data.repository.SessionEmbeddingRepository
import com.example.star.aiwork.domain.model.embedding.SessionEmbedding

/**
 * 保存会话向量嵌入的用例
 */
class SaveSessionEmbeddingUseCase(
    private val repository: SessionEmbeddingRepository
) {
    /**
     * 执行保存操作
     *
     * @param embedding 要保存的会话向量
     */
    suspend operator fun invoke(embedding: SessionEmbedding) {
        repository.saveSessionEmbedding(embedding)
    }
}

