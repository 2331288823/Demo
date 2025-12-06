package com.example.star.aiwork.data.local

import android.content.Context
import androidx.room.Room

object EmbeddingDatabaseProvider {
    private var INSTANCE: EmbeddingDatabase? = null

    fun getDatabase(context: Context): EmbeddingDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                EmbeddingDatabase::class.java,
                "embedding_database"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}

