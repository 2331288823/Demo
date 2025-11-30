package com.example.star.aiwork.data.local.datasource

import com.example.star.aiwork.data.local.record.MessageRecord
import kotlinx.coroutines.flow.Flow

interface MessageLocalDataSource {

    suspend fun insertMessage(message: MessageRecord)

    suspend fun getMessage(id: String): MessageRecord?

    suspend fun getMessages(sessionId: String): List<MessageRecord>

    fun observeMessages(sessionId: String): Flow<List<MessageRecord>>

    suspend fun deleteMessage(id: String)

    suspend fun deleteMessagesBySession(sessionId: String)
}
