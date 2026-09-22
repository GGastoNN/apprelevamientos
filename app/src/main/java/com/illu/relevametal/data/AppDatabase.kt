package com.illu.relevametal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        SpaceEntity::class,
        OpeningEntity::class,
        EvidenceEntity::class,
        EventEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun spaces(): SpaceDao
    abstract fun openings(): OpeningDao
    abstract fun evidence(): EvidenceDao
    abstract fun events(): EventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN status TEXT NOT NULL DEFAULT 'EN_CURSO'")
                db.execSQL("ALTER TABLE projects ADD COLUMN responsible TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE spaces ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE openings ADD COLUMN wallThicknessMm INTEGER")
                db.execSQL("ALTER TABLE openings ADD COLUMN depthMm INTEGER")
                db.execSQL("ALTER TABLE openings ADD COLUMN clearanceLeftMm INTEGER")
                db.execSQL("ALTER TABLE openings ADD COLUMN clearanceRightMm INTEGER")
                db.execSQL("ALTER TABLE openings ADD COLUMN clearanceTopMm INTEGER")
                db.execSQL("ALTER TABLE openings ADD COLUMN clearanceBottomMm INTEGER")
                db.execSQL("ALTER TABLE openings ADD COLUMN plumbState TEXT NOT NULL DEFAULT 'NO_VERIFICADO'")
                db.execSQL("ALTER TABLE openings ADD COLUMN levelState TEXT NOT NULL DEFAULT 'NO_VERIFICADO'")
                db.execSQL("ALTER TABLE openings ADD COLUMN squareState TEXT NOT NULL DEFAULT 'NO_VERIFICADO'")
                db.execSQL("ALTER TABLE openings ADD COLUMN floorState TEXT NOT NULL DEFAULT 'NO_VERIFICADO'")
                db.execSQL("ALTER TABLE openings ADD COLUMN plasterState TEXT NOT NULL DEFAULT 'NO_VERIFICADO'")
                db.execSQL("ALTER TABLE openings ADD COLUMN premarcoState TEXT NOT NULL DEFAULT 'NO_VERIFICADO'")
                db.execSQL("ALTER TABLE openings ADD COLUMN openingDirection TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE openings ADD COLUMN interference TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE evidence ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE evidence ADD COLUMN isPrimary INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE events ADD COLUMN openingId INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN severity TEXT NOT NULL DEFAULT 'INFO'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE evidence ADD COLUMN rotationDegrees INTEGER NOT NULL DEFAULT 0")
            }
        }


        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE evidence ADD COLUMN phase TEXT NOT NULL DEFAULT 'GENERAL'")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "relevametal.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
