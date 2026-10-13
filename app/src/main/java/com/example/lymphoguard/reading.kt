package com.example.lymphoguard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readings")
data class Reading(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fsr: String,

    val circumference: String,

    val status: String,

    val date: String,

    val time: String
)