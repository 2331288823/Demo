package com.example.star.aiwork.data.local.datasource

import com.example.star.aiwork.data.local.record.DraftRecord
import kotlinx.coroutines.flow.Flow

interface DraftLocalDataSource {

    suspend fun upsertDraft(draft: DraftRecord)

    suspend fun getDraft(sessionId: String): DraftRecord?

    fun observeDraft(sessionId: String): Flow<DraftRecord?>

    suspend fun deleteDraft(sessionId: String)
}
