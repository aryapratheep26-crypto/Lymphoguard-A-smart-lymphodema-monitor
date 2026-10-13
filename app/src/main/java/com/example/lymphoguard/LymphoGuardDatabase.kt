package com.example.lymphoguard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Reading::class],
    version = 1,
    exportSchema = false
)
abstract class LymphoGuardDatabase : RoomDatabase() {

    abstract fun readingDao(): ReadingDao

    companion object {

        @Volatile
        private var INSTANCE: LymphoGuardDatabase? = null

        fun getDatabase(
            context: Context
        ): LymphoGuardDatabase {

            return INSTANCE
                ?: synchronized(this) {

                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            LymphoGuardDatabase::class.java,
                            "lymphoguard_database"
                        ).build()

                    INSTANCE = instance

                    instance
                }
        }
    }
}