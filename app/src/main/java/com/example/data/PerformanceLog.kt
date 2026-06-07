package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "performance_logs")
data class PerformanceLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateString: String, // e.g., "June 7, 2026"
    val scorePercentage: Int,
    val completedTasksCount: Int,
    val totalTasksCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
