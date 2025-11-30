package com.example.star.aiwork.data.local.datasource

import android.content.ContentValues
import android.content.Context
import com.example.star.aiwork.data.local.db.ChatDatabase
import com.example.star.aiwork.data.local.record.MessageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MessageLocalDataSourceImpl(context: Context) : MessageLocalDataSource {

    private val dbHelper = ChatDatabase(context)

    override suspend fun insertMessage(message: MessageRecord) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", message.id)
            put("sessionId", message.sessionId)
            put("role", message.role)
            put("content", message.content)
            put("createdAt", message.createdAt)
            put("status", message.status)
            put("parentMessageId", message.parentMessageId)
        }
        db.insertWithOnConflict("messages", null, values, 5 /* CONFLICT_REPLACE */)
    }

    override suspend fun getMessage(id: String): MessageRecord? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "messages",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        )

        val record = if (cursor.moveToFirst()) {
            MessageRecord(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                sessionId = cursor.getString(cursor.getColumnIndexOrThrow("sessionId")),
                role = cursor.getString(cursor.getColumnIndexOrThrow("role")),
                content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                status = cursor.getInt(cursor.getColumnIndexOrThrow("status")),
                parentMessageId = cursor.getString(cursor.getColumnIndexOrThrow("parentMessageId"))
            )
        } else null

        cursor.close()
        return record
    }

    override suspend fun getMessages(sessionId: String): List<MessageRecord> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "messages",
            null,
            "sessionId = ?",
            arrayOf(sessionId),
            null,
            null,
            "createdAt ASC"
        )

        val list = mutableListOf<MessageRecord>()
        while (cursor.moveToNext()) {
            list.add(
                MessageRecord(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    sessionId = cursor.getString(cursor.getColumnIndexOrThrow("sessionId")),
                    role = cursor.getString(cursor.getColumnIndexOrThrow("role")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow("status")),
                    parentMessageId = cursor.getString(cursor.getColumnIndexOrThrow("parentMessageId"))
                )
            )
        }

        cursor.close()
        return list
    }
    override fun observeMessages(sessionId: String): Flow<List<MessageRecord>> = flow {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "messages",
            null,
            "sessionId = ?",
            arrayOf(sessionId),
            null,
            null,
            "createdAt ASC"
        )

        val list = mutableListOf<MessageRecord>()
        while (cursor.moveToNext()) {
            list.add(
                MessageRecord(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    sessionId = cursor.getString(cursor.getColumnIndexOrThrow("sessionId")),
                    role = cursor.getString(cursor.getColumnIndexOrThrow("role")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow("status")),
                    parentMessageId = cursor.getString(cursor.getColumnIndexOrThrow("parentMessageId"))
                )
            )
        }

        cursor.close()
        emit(list)
    }

    override suspend fun deleteMessage(id: String) {
        val db = dbHelper.writableDatabase
        db.delete("messages", "id = ?", arrayOf(id))
    }

    override suspend fun deleteMessagesBySession(sessionId: String) {
        val db = dbHelper.writableDatabase
        db.delete("messages", "sessionId = ?", arrayOf(sessionId))
    }
}
