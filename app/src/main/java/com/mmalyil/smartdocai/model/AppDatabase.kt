package com.mmalyil.smartdocai.model


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScannedFile::class], version = 1)
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
                ).build().also { INSTANCE = it }
            }
        }
    }
}