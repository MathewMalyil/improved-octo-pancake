package com.mmalyil.smartdocai.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration


@Database(
    entities = [ScannedFile::class],
    version = 1,
    exportSchema = true // keep schema history for proper migrations
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scannedFileDao(): ScannedFileDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // ⚠️ Add real Migration objects here as you bump versions
        private val MIGRATIONS: Array<Migration> = arrayOf(
            // Example (uncomment & bump version when you actually change schema):
            // object : Migration(1, 2) {
            //     override fun migrate(db: SupportSQLiteDatabase) {
            //         db.execSQL(
            //             "ALTER TABLE ScannedFile ADD COLUMN lastAnalyzedAt INTEGER"
            //         )
            //     }
            // }
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartdoc_db"
                )
                    // Do NOT use fallbackToDestructiveMigration() in production
                    .addMigrations(*MIGRATIONS)
                    .enableMultiInstanceInvalidation() // safe for multi-process/use cases
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}