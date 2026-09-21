package com.illu.relevametal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, SpaceEntity::class, OpeningEntity::class, EvidenceEntity::class, EventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun spaces(): SpaceDao
    abstract fun openings(): OpeningDao
    abstract fun evidence(): EvidenceDao
    abstract fun events(): EventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "relevametal.db")
                .build().also { instance = it }
        }
    }
}
