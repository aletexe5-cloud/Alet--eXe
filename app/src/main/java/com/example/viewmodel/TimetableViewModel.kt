package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.receiver.AlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TimetableViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repository: TaskRepository

    init {
        val db = AppDatabase.getDatabase(context)
        repository = TaskRepository(db.taskDao(), db.performanceLogDao())
    }

    val allTasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allLogs: StateFlow<List<PerformanceLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Real-time ticking state representing current time
    private val _currentTime = MutableStateFlow(Calendar.getInstance())
    val currentTime = _currentTime.asStateFlow()

    // Screen display trigger for showing performance report pop ups inside the app UI
    private val _showPerformanceSummary = MutableStateFlow<PerformanceReport?>(null)
    val showPerformanceSummary = _showPerformanceSummary.asStateFlow()

    // Global Urdu Toggle support state (Settings ON/OFF status)
    private val _isUrduEnabled = MutableStateFlow(false)
    val isUrduEnabled = _isUrduEnabled.asStateFlow()

    data class PerformanceReport(
        val percentage: Int,
        val completedCount: Int,
        val totalCount: Int,
        val date: String
    )

    init {
        // Pre-populate 5 daily prayer tasks if DB is completely empty
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getTasksSync()
            if (existing.isEmpty()) {
                populatePrayerSchedule()
            } else {
                rescheduleAllAlarms(existing)
            }
        }

        // Continuous ticking clock loop
        viewModelScope.launch {
            while (true) {
                val now = Calendar.getInstance()
                _currentTime.value = now
                checkTaskTransitionsAndAutoCross(now)
                delay(1000)
            }
        }
    }

    fun toggleUrduSupport(enabled: Boolean) {
        _isUrduEnabled.value = enabled
    }

    private suspend fun populatePrayerSchedule() {
        val prayers = listOf(
            Triple("Fajr Prayer (فجر کی نماز)", "04:30", "05:30"),
            Triple("Dhuhr Prayer (ظہر کی نماز)", "13:15", "14:15"),
            Triple("Asr Prayer (عصر کی نماز)", "16:45", "17:45"),
            Triple("Maghrib Prayer (مغرب کی نماز)", "19:10", "19:50"),
            Triple("Isha Prayer (عشاء کی نماز)", "21:00", "22:00")
        )
        prayers.forEachIndexed { index, prayer ->
            val task = Task(
                name = prayer.first,
                startTime = prayer.second,
                endTime = prayer.third,
                displayOrder = index + 1,
                status = "PENDING",
                isLocked = true,
                isNew = false,
                notified = false,
                endNotified = false
            )
            val id = repository.insertTask(task)
            AlarmReceiver.scheduleAlarmForTask(context, task.copy(id = id.toInt()))
        }
        rescheduleDailyResetTimer(repository.getTasksSync())
    }

    private fun rescheduleAllAlarms(tasks: List<Task>) {
        tasks.forEach { task ->
            AlarmReceiver.scheduleAlarmForTask(context, task)
        }
        rescheduleDailyResetTimer(tasks)
    }

    fun dismissPerformanceReport() {
        _showPerformanceSummary.value = null
    }

    fun addTask(name: String, startTime: String, endTime: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = allTasks.value
            val order = tasks.size + 1
            val newTask = Task(
                name = name,
                startTime = startTime,
                endTime = endTime,
                displayOrder = order,
                status = "PENDING",
                isLocked = false,
                isNew = true,
                notified = false,
                endNotified = false
            )
            val id = repository.insertTask(newTask)
            val scheduledTask = newTask.copy(id = id.toInt())
            
            AlarmReceiver.scheduleAlarmForTask(context, scheduledTask)
            rescheduleDailyResetTimer(tasks + scheduledTask)
        }
    }

    fun updateTaskTimings(task: Task, startTime: String, endTime: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(
                startTime = startTime,
                endTime = endTime,
                notified = false,
                endNotified = false
            )
            repository.updateTask(updated)
            // Cancel and reschedule
            AlarmReceiver.cancelAlarmForTask(context, task.id)
            AlarmReceiver.scheduleAlarmForTask(context, updated)
            rescheduleDailyResetTimer(allTasks.value.map { if (it.id == task.id) updated else it })
        }
    }

    fun updateTaskName(task: Task, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(name = newName)
            repository.updateTask(updated)
        }
    }

    fun toggleTaskLock(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(isLocked = !task.isLocked)
            repository.updateTask(updated)
        }
    }

    fun updateTaskStatus(task: Task, isCompleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (isCompleted) "COMPLETED" else "PENDING"
            val updated = task.copy(status = newStatus)
            repository.updateTask(updated)
        }
    }

    fun deleteTask(task: Task) {
        // Only allow deleting if UNLOCKED
        if (task.isLocked) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task)
            AlarmReceiver.cancelAlarmForTask(context, task.id)
            val remainingTasks = allTasks.value.filter { it.id != task.id }
            rescheduleDailyResetTimer(remainingTasks)
        }
    }

    fun calculatePercentage(tasks: List<Task>): Int {
        if (tasks.isEmpty()) return 0
        val completed = tasks.count { it.status == "COMPLETED" }
        return (completed * 100) / tasks.size
    }

    private fun rescheduleDailyResetTimer(tasks: List<Task>) {
        AlarmReceiver.cancelDailyReset(context)
        if (tasks.isNotEmpty()) {
            val lastTask = tasks.maxByOrNull { it.endTime }
            if (lastTask != null) {
                AlarmReceiver.scheduleDailyReset(context, lastTask.endTime)
            }
        }
    }

    private fun checkTaskTransitionsAndAutoCross(now: Calendar) {
        val tasks = allTasks.value
        if (tasks.isEmpty()) return

        val hourNow = now.get(Calendar.HOUR_OF_DAY)
        val minuteNow = now.get(Calendar.MINUTE)
        val timeNowStr = String.format(Locale.getDefault(), "%02d:%02d", hourNow, minuteNow)

        viewModelScope.launch(Dispatchers.IO) {
            tasks.forEach { task ->
                // Start Time Trigger (Reminds at Start Time)
                if (task.startTime == timeNowStr && !task.notified) {
                    val updated = task.copy(notified = true)
                    repository.updateTask(updated)
                    sendLocalBroadcast(AlarmReceiver.ACTION_TASK_REMINDER, task)
                }

                // End Time Trigger (If PENDING, mark as FAILED and log/alert)
                if (task.endTime == timeNowStr && !task.endNotified) {
                    if (task.status == "PENDING") {
                        val updated = task.copy(status = "FAILED", endNotified = true)
                        repository.updateTask(updated)
                        sendLocalBroadcast(AlarmReceiver.ACTION_TASK_END_REMINDER, task)
                    } else {
                        val updated = task.copy(endNotified = true)
                        repository.updateTask(updated)
                    }
                }
            }

            // Auto-check for end-of-day finalization (5 minutes after the last task of the day reaches its End Time)
            val lastTask = tasks.maxByOrNull { it.endTime }
            if (lastTask != null) {
                val parts = lastTask.endTime.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: return@launch
                    val minute = parts[1].toIntOrNull() ?: return@launch

                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.MINUTE, 5) // 5 minutes delay block
                    }

                    if (now.timeInMillis >= targetCal.timeInMillis) {
                        // Check if we already logged today's result
                        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        val todayStr = dateFormat.format(Calendar.getInstance().time)
                        
                        // Query existing logs to avoid duplicate
                        val currentLogs = repository.allLogs.stateIn(viewModelScope).value
                        val loggedToday = currentLogs.any { it.dateString == todayStr }

                        if (!loggedToday) {
                            val total = tasks.size
                            val completed = tasks.count { it.status == "COMPLETED" }
                            val percent = if (total > 0) (completed * 100) / total else 0

                            // Insert into history
                            repository.insertLog(
                                PerformanceLog(
                                    dateString = todayStr,
                                    scorePercentage = percent,
                                    completedTasksCount = completed,
                                    totalTasksCount = total,
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            // Show standard visual report immediately
                            _showPerformanceSummary.value = PerformanceReport(
                                percentage = percent,
                                completedCount = completed,
                                totalCount = total,
                                date = todayStr
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sendLocalBroadcast(actionStr: String, task: Task) {
        val intent = android.content.Intent(context, AlarmReceiver::class.java).apply {
            action = actionStr
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_TASK_NAME, task.name)
            putExtra(AlarmReceiver.EXTRA_TASK_TIME, if (actionStr == AlarmReceiver.ACTION_TASK_REMINDER) task.startTime else task.endTime)
        }
        context.sendBroadcast(intent)
    }

    fun isScoreUnlocked(now: Calendar): Boolean {
        val tasks = allTasks.value
        if (tasks.isEmpty()) return false
        val lastTask = tasks.maxByOrNull { it.endTime } ?: return false
        val parts = lastTask.endTime.split(":")
        if (parts.size != 2) return false
        val hour = parts[0].toIntOrNull() ?: return false
        val minute = parts[1].toIntOrNull() ?: return false

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 5)
        }
        return now.timeInMillis >= targetCal.timeInMillis
    }

    fun getFinalizationTimeString(): String {
        val tasks = allTasks.value
        if (tasks.isEmpty()) return ""
        val lastTask = tasks.maxByOrNull { it.endTime } ?: return ""
        val parts = lastTask.endTime.split(":")
        if (parts.size != 2) return ""
        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 5)
        }
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(targetCal.time)
    }

    fun getCountdownSecondsRemaining(now: Calendar): Int {
        val tasks = allTasks.value
        if (tasks.isEmpty()) return 0
        val lastTask = tasks.maxByOrNull { it.endTime } ?: return 0
        val parts = lastTask.endTime.split(":")
        if (parts.size != 2) return 0
        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, 5)
        }

        val diff = targetCal.timeInMillis - now.timeInMillis
        return if (diff > 0) (diff / 1000).toInt() else 0
    }
}
