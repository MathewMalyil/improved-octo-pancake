package com.mmalyil.smartdocai.model


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(entities = [ScannedFile::class], version = 1) // ✅ Version bumped
abstract class AppDatabase : RoomDatabase() {
    abstract fun scannedFileDao(): ScannedFileDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartdoc_db"
                )
                    .fallbackToDestructiveMigration() // ✅ Keep this for dev
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}