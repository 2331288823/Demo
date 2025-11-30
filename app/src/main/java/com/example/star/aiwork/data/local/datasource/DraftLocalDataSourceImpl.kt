package com.example.star.aiwork.data.local.datasource

import android.content.ContentValues
import android.content.Context
import com.example.star.aiwork.data.local.db.ChatDatabase
import com.example.star.aiwork.data.local.record.DraftRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DraftLocalDataSourceImpl(context: Context) : DraftLocalDataSource {

    private val dbHelper = ChatDatabase(context)

    override suspend fun upsertDraft(draft: DraftRecord) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("sessionId", draft.sessionId)
            put("content", draft.content)
            put("updatedAt", draft.updatedAt)
        }
        db.insertWithOnConflict("drafts", null, values, 5 /* CONFLICT_REPLACE */)
    }

    override suspend fun getDraft(sessionId: String): DraftRecord? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "drafts",
            null,
            "sessionId = ?",
            arrayOf(sessionId),
            null,
            null,
            null
        )

        val record = if (cursor.moveToFirst()) {
            DraftRecord(
                sessionId = cursor.getString(cursor.getColumnIndexOrThrow("sessionId")),
                content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt"))
            )
        } else null

        cursor.close()
        return record
    }

    override fun observeDraft(sessionId: String): Flow<DraftRecord?> = flow {
        emit(getDraft(sessionId))
    }

    override suspend fun deleteDraft(sessionId: String) {
        val db = dbHelper.writableDatabase
        db.delete("drafts", "sessionId = ?", arrayOf(sessionId))
    }
}
