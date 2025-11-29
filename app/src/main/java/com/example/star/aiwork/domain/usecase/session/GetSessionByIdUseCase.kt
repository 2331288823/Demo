package com.example.star.aiwork.domain.usecase.session

import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.repository.SessionRepository

class GetSessionByIdUseCase(private val sessionRepository: SessionRepository) {
    /**
     * Invokes the use case to get a specific session by its ID.
     *
     * @param sessionId The ID of the session to retrieve.
     * @return The [SessionEntity] if found, otherwise null.
     */
    suspend operator fun invoke(sessionId: String): SessionEntity? {
        return sessionRepository.getSession(sessionId)
    }
}
