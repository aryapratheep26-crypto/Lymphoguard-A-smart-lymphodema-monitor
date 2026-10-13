package com.example.lymphoguard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: Reading)

    @Query("SELECT * FROM readings ORDER BY id DESC")
    suspend fun getAllReadings(): List<Reading>
}