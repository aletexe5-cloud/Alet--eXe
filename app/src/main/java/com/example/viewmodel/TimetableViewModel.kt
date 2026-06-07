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

    data class PerformanceReport(
        val percentage: Int,
        val completedCount: Int,
        val totalCount: Int,
        val date: String
    )

    init {
        // Continuous clock updating for UI and active triggers inside active app container
        viewModelScope.launch {
            while (true) {
                val now = Calendar.getInstance()
                _currentTime.value = now
                checkTaskRemindersAndTrailingReset(now)
                delay(1000)
            }
        }
    }

    fun dismissPerformanceReport() {
        _showPerformanceSummary.value = null
    }

    fun addTask(name: String, targetTime: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = allTasks.value
            val order = tasks.size + 1
            val newTask = Task(
                name = name,
                targetTime = targetTime,
                displayOrder = order,
                status = "PENDING",
                isNew = true,
                notified = false
            )
            val id = repository.insertTask(newTask)
            val scheduledTask = newTask.copy(id = id.toInt())
            
            AlarmReceiver.scheduleAlarmForTask(context, scheduledTask)
            rescheduleDailyResetTimer(tasks + scheduledTask)
        }
    }

    fun updateTaskStatus(task: Task, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTask(task.copy(status = newStatus))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task)
            AlarmReceiver.cancelAlarmForTask(context, task.id)
            val remainingTasks = allTasks.value.filter { it.id != task.id }
            rescheduleDailyResetTimer(remainingTasks)
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            allTasks.value.forEach {
                AlarmReceiver.cancelAlarmForTask(context, it.id)
            }
            AlarmReceiver.cancelDailyReset(context)
            repository.clearAllTasks()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllLogs()
        }
    }

    fun calculatePercentage(tasks: List<Task>): Int {
        if (tasks.isEmpty()) return 0
        val completed = tasks.count { it.status == "COMPLETED" }
        return (completed * 100) / tasks.size
    }

    fun populatePresetSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            // Cancel current task alarms
            allTasks.value.forEach { AlarmReceiver.cancelAlarmForTask(context, it.id) }
            repository.clearAllTasks()

            // 20 realistic bilingual daily timetable tasks representing Urdu-English combo
            val presets = listOf(
                Pair("Fajr Prayer (فجر کی نماز)", "04:30"),
                Pair("Quran Recitation (تلاوتِ قرآن)", "05:00"),
                Pair("Morning Workout (صبح کی ورزش)", "06:00"),
                Pair("Healthy Breakfast (صحت بخش ناشتہ)", "07:00"),
                Pair("Commute & Plan (آفس روانگی اور منصوبہ بندی)", "08:00"),
                Pair("Project Deep Work 1 (پروجیکٹ گہرا کام ۱)", "09:00"),
                Pair("Coffee Break (چائے کا وقفہ)", "11:00"),
                Pair("Project Deep Work 2 (پروجیکٹ گہرا کام ۲)", "11:30"),
                Pair("Dhuhr Prayer & Lunch (نمازِ ظہر اور دوپہر کا کھانا)", "13:15"),
                Pair("Email & Team Synced (ای میلز اور ٹیم مٹینگ)", "14:15"),
                Pair("Technical Skill Learning (تکنیکی مہارت سیکھنا)", "15:00"),
                Pair("Asr Prayer (نمازِ عصر)", "16:45"),
                Pair("Evening Tea & Reflection (شام کی چائے اور سوچ بچار)", "17:15"),
                Pair("Maghrib Prayer (نمازِ مغرب)", "19:10"),
                Pair("English / Urdu Translation Work (ترجمہ کا کام)", "19:40"),
                Pair("Dinner with Family (خاندان کے ساتھ رات کا کھانا)", "20:30"),
                Pair("Isha Prayer (نمازِ عشاء)", "21:30"),
                Pair("Daily Performance Review (روزانہ کارکردگی کا جائزہ)", "22:00"),
                Pair("Journaling & Reading (ڈائری اور مطالعہ)", "22:30"),
                Pair("Sleep Routine (سونے کی تیاری)", "23:00")
            )

            presets.forEachIndexed { index, preset ->
                val task = Task(
                    name = preset.first,
                    targetTime = preset.second,
                    displayOrder = index + 1,
                    status = "PENDING",
                    isNew = true,
                    notified = false
                )
                val id = repository.insertTask(task)
                AlarmReceiver.scheduleAlarmForTask(context, task.copy(id = id.toInt()))
            }

            val tasksFromPreset = repository.getTasksSync()
            rescheduleDailyResetTimer(tasksFromPreset)
        }
    }

    private fun rescheduleDailyResetTimer(tasks: List<Task>) {
        AlarmReceiver.cancelDailyReset(context)
        if (tasks.isNotEmpty()) {
            val lastTask = tasks.maxByOrNull { it.targetTime }
            if (lastTask != null) {
                AlarmReceiver.scheduleDailyReset(context, lastTask.targetTime)
            }
        }
    }

    fun simulateTaskReminder(task: Task) {
        viewModelScope.launch(Dispatchers.Main) {
            val intent = android.content.Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_TASK_REMINDER
                putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
                putExtra(AlarmReceiver.EXTRA_TASK_NAME, task.name)
                putExtra(AlarmReceiver.EXTRA_TASK_TIME, task.targetTime)
            }
            context.sendBroadcast(intent)
        }
    }

    fun simulateDailyReset() {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = allTasks.value
            if (tasks.isNotEmpty()) {
                val total = tasks.size
                val completed = tasks.count { it.status == "COMPLETED" }
                val percent = if (total > 0) (completed * 100) / total else 0

                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val dateStr = dateFormat.format(Calendar.getInstance().time)

                // Trigger popup immediately in the foreground app state
                _showPerformanceSummary.value = PerformanceReport(
                    percentage = percent,
                    completedCount = completed,
                    totalCount = total,
                    date = dateStr
                )

                // Log into history database
                repository.insertLog(
                    PerformanceLog(
                        dateString = dateStr,
                        scorePercentage = percent,
                        completedTasksCount = completed,
                        totalTasksCount = total,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // Reset all tasks back to pending state
                repository.resetAllTasks()
                Log.d("TimetableViewModel", "Forced reset complete. Logs inserted successfully.")
            }
        }
    }

    private fun checkTaskRemindersAndTrailingReset(now: Calendar) {
        val tasks = allTasks.value
        if (tasks.isEmpty()) return

        val hourNow = now.get(Calendar.HOUR_OF_DAY)
        val minuteNow = now.get(Calendar.MINUTE)
        val timeNowStr = String.format(Locale.getDefault(), "%02d:%02d", hourNow, minuteNow)

        viewModelScope.launch(Dispatchers.IO) {
            // Task alarm matching
            tasks.forEach { task ->
                if (task.targetTime == timeNowStr && !task.notified) {
                    repository.updateTask(task.copy(notified = true))
                    
                    val intent = android.content.Intent(context, AlarmReceiver::class.java).apply {
                        action = AlarmReceiver.ACTION_TASK_REMINDER
                        putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
                        putExtra(AlarmReceiver.EXTRA_TASK_NAME, task.name)
                        putExtra(AlarmReceiver.EXTRA_TASK_TIME, task.targetTime)
                    }
                    context.sendBroadcast(intent)
                }
            }

            // Calculation and reset boundary matching (exactly 10 minutes trailing after the last task of the day ends)
            val lastTask = tasks.maxByOrNull { it.targetTime }
            if (lastTask != null) {
                val parts = lastTask.targetTime.split(":")
                if (parts.size == 2) {
                    val hour = parts[0].toIntOrNull() ?: return@launch
                    val minute = parts[1].toIntOrNull() ?: return@launch

                    val targetCal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.MINUTE, 10) // exactly 10 minutes trailing limit
                    }

                    val trailingHour = targetCal.get(Calendar.HOUR_OF_DAY)
                    val trailingMin = targetCal.get(Calendar.MINUTE)
                    
                    if (hourNow == trailingHour && minuteNow == trailingMin) {
                        val pendingCount = tasks.count { it.status != "PENDING" }
                        // Ensure we have something marked before resetting so logic doesn't fire continuously
                        if (pendingCount > 0 && _showPerformanceSummary.value == null) {
                            simulateDailyReset()
                        }
                    }
                }
            }
        }
    }

    fun getTrailingResetSecondsRemaining(now: Calendar): Int? {
        val tasks = allTasks.value
        if (tasks.isEmpty()) return null
        val lastTask = tasks.maxByOrNull { it.targetTime } ?: return null
        val parts = lastTask.targetTime.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
            timeInMillis = startCal.timeInMillis
            add(Calendar.MINUTE, 10)
        }

        val nowMs = now.timeInMillis
        if (nowMs >= startCal.timeInMillis && nowMs < endCal.timeInMillis) {
            val diffSeconds = ((endCal.timeInMillis - nowMs) / 1000).toInt()
            val hasAtLeastOneMarked = tasks.any { it.status != "PENDING" }
            if (hasAtLeastOneMarked && diffSeconds > 0) {
                return diffSeconds
            }
        }
        return null
    }
}
