package com.example.star.aiwork.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.star.aiwork.data.local.converter.Converters
import com.example.star.aiwork.data.local.dao.EmbeddingDao
import com.example.star.aiwork.domain.model.EmbeddingEntity

@Database(
    entities = [EmbeddingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EmbeddingDatabase : RoomDatabase() {
    abstract fun embeddingDao(): EmbeddingDao
}

