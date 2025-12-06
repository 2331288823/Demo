package com.example.star.aiwork.data.repository

import com.example.star.aiwork.data.local.dao.SessionEmbeddingDao
import com.example.star.aiwork.data.repository.mapper.toDomain
import com.example.star.aiwork.data.repository.mapper.toEntity
import com.example.star.aiwork.domain.model.embedding.SessionEmbedding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionEmbeddingRepositoryImpl(
    private val dao: SessionEmbeddingDao
) : SessionEmbeddingRepository {

    override suspend fun saveSessionEmbedding(embedding: SessionEmbedding) {
        dao.insert(embedding.toEntity())
    }

    override suspend fun getEmbeddingsBySession(sessionId: String): List<SessionEmbedding> {
        return dao.getEmbeddingsBySession(sessionId).map { it.toDomain() }
    }

    override fun observeEmbeddingsBySession(sessionId: String): Flow<List<SessionEmbedding>> {
        return dao.observeEmbeddingsBySession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEmbeddingBySessionAndText(sessionId: String, text: String): SessionEmbedding? {
        return dao.getEmbeddingBySessionAndText(sessionId, text)?.toDomain()
    }

    override suspend fun getAllSessionEmbeddings(): List<SessionEmbedding> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun deleteEmbeddingsBySession(sessionId: String) {
        dao.deleteEmbeddingsBySession(sessionId)
    }
}

