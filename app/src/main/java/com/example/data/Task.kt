package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startTime: String, // format: "HH:mm"
    val endTime: String,   // format: "HH:mm"
    val displayOrder: Int,
    val status: String = "PENDING", // PENDING, COMPLETED, FAILED
    val isLocked: Boolean = false,  // true for permanent prayer tasks or locked custom tasks
    val isNew: Boolean = true,
    val notified: Boolean = false,
    val endNotified: Boolean = false
)
