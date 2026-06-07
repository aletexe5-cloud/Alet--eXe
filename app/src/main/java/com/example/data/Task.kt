package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetTime: String, // format: "HH:mm"
    val displayOrder: Int,
    val status: String = "PENDING", // PENDING, COMPLETED, INCOMPLETE
    val isNew: Boolean = true,
    val notified: Boolean = false
)
