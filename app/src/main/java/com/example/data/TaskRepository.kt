package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao,
    private val performanceLogDao: PerformanceLogDao
) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val allLogs: Flow<List<PerformanceLog>> = performanceLogDao.getAllLogs()

    suspend fun getTasksSync(): List<Task> = taskDao.getTasksSync()

    suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: Int) {
        taskDao.deleteTaskById(id)
    }

    suspend fun updateTaskStatus(id: Int, status: String) {
        taskDao.updateTaskStatus(id, status)
    }

    suspend fun clearNewStatus() {
        taskDao.clearNewStatus()
    }

    suspend fun resetAllTasks() {
        taskDao.resetAllTasks()
    }

    suspend fun clearAllTasks() {
        taskDao.clearAllTasks()
    }

    // Performance log operations
    suspend fun insertLog(log: PerformanceLog) {
        performanceLogDao.insertLog(log)
    }

    suspend fun deleteLog(log: PerformanceLog) {
        performanceLogDao.deleteLog(log)
    }

    suspend fun clearAllLogs() {
        performanceLogDao.clearAllLogs()
    }
}
