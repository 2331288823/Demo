package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.dao.GlobalEmbeddingDao
import com.example.star.aiwork.data.repository.mapper.toDomain
import com.example.star.aiwork.data.repository.mapper.toEntity
import com.example.star.aiwork.domain.model.embedding.GlobalEmbedding

class GlobalEmbeddingRepositoryImpl(
    private val dao: GlobalEmbeddingDao
) : GlobalEmbeddingRepository {

    override suspend fun saveGlobalEmbedding(embedding: GlobalEmbedding) {
        dao.insert(embedding.toEntity())
    }

    override suspend fun getGlobalEmbedding(id: Int): GlobalEmbedding? {
        return dao.getEmbedding(id)?.toDomain()
    }

    override suspend fun getGlobalEmbeddingByText(text: String): GlobalEmbedding? {
        return dao.getEmbeddingByText(text)?.toDomain()
    }

    override suspend fun getAllGlobalEmbeddings(): List<GlobalEmbedding> {
        return dao.getAll().map { it.toDomain() }
    }
}

