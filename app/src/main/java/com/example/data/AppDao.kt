package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY startTime ASC, id ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY startTime ASC, id ASC")
    suspend fun getTasksSync(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: Int, status: String)

    @Query("UPDATE tasks SET isNew = 0")
    suspend fun clearNewStatus()

    @Query("UPDATE tasks SET status = 'PENDING', notified = 0, endNotified = 0, isNew = 0")
    suspend fun resetAllTasks()

    @Query("DELETE FROM tasks WHERE isLocked = 0")
    suspend fun deleteUnlockedTasks()

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()
}

@Dao
interface PerformanceLogDao {
    @Query("SELECT * FROM performance_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<PerformanceLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PerformanceLog)

    @Delete
    suspend fun deleteLog(log: PerformanceLog)

    @Query("DELETE FROM performance_logs")
    suspend fun clearAllLogs()
}
