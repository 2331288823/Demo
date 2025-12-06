package com.example.star.aiwork.domain.usecase.embedding

import com.example.star.aiwork.data.repository.GlobalEmbeddingRepository
import com.example.star.aiwork.domain.model.embedding.GlobalEmbedding

/**
 * 保存全局向量嵌入的用例
 */
class SaveGlobalEmbeddingUseCase(
    private val repository: GlobalEmbeddingRepository
) {
    /**
     * 执行保存操作
     *
     * @param embedding 要保存的全局向量
     */
    suspend operator fun invoke(embedding: GlobalEmbedding) {
        repository.saveGlobalEmbedding(embedding)
    }
}

