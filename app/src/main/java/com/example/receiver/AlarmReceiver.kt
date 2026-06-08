package com.example.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.PerformanceLog
import com.example.data.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("AlarmReceiver", "Received action: $action")
        val db = AppDatabase.getDatabase(context)
        val taskDao = db.taskDao()
        val logDao = db.performanceLogDao()

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_TASK_REMINDER -> {
                        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
                        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Task Schedule"
                        val taskTime = intent.getStringExtra(EXTRA_TASK_TIME) ?: ""

                        if (taskId != -1) {
                            val tasks = taskDao.getTasksSync()
                            val t = tasks.find { it.id == taskId }
                            if (t != null) {
                                taskDao.updateTask(t.copy(notified = true))
                            }
                        }

                        showNotification(
                            context = context,
                            title = "TimeFlow: $taskName Starts Now",
                            text = "It's $taskTime! Time to perform your scheduled task.",
                            notificationId = taskId.coerceAtLeast(1)
                        )
                    }
                    ACTION_TASK_END_REMINDER -> {
                        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
                        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Task Schedule"
                        val taskTime = intent.getStringExtra(EXTRA_TASK_TIME) ?: ""

                        if (taskId != -1) {
                            val tasks = taskDao.getTasksSync()
                            val t = tasks.find { it.id == taskId }
                            if (t != null && t.status == "PENDING") {
                                // Auto mark as FAILED
                                taskDao.updateTask(t.copy(status = "FAILED", endNotified = true))
                                
                                showNotification(
                                    context = context,
                                    title = "TimeFlow: Task Missed! ❌",
                                    text = "Task '$taskName' has reached its End Time ($taskTime) and is marked as Failed.",
                                    notificationId = taskId + 20002
                                )
                            }
                        }
                    }
                    ACTION_DAILY_RESET -> {
                        val tasks = taskDao.getTasksSync()
                        if (tasks.isNotEmpty()) {
                            val total = tasks.size
                            val completed = tasks.count { it.status == "COMPLETED" }
                            val percent = if (total > 0) (completed * 100) / total else 0

                            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            val dateStr = dateFormat.format(Calendar.getInstance().time)

                            // Save performance log
                            logDao.insertLog(
                                PerformanceLog(
                                    dateString = dateStr,
                                    scorePercentage = percent,
                                    completedTasksCount = completed,
                                    totalTasksCount = total,
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            // Show final notification
                            showNotification(
                                context = context,
                                title = "TimeFlow: Daily Reset Completed!",
                                text = "Your daily success rate: $percent%. Schedule reset for tomorrow.",
                                notificationId = 9999
                            )

                            // Delete unlocked tasks and reset locked tasks
                            taskDao.deleteUnlockedTasks()
                            taskDao.resetAllTasks()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error processing alarm", e)
            } catch (t: Throwable) {
                Log.e("AlarmReceiver", "Fatal throwable alarm logic", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, text: String, notificationId: Int) {
        val channelId = "timeflow_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TimeFlow Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for TimeFlow tasks and alerts"
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        const val ACTION_TASK_REMINDER = "com.example.receiver.ACTION_TASK_REMINDER"
        const val ACTION_TASK_END_REMINDER = "com.example.receiver.ACTION_TASK_END_REMINDER"
        const val ACTION_DAILY_RESET = "com.example.receiver.ACTION_DAILY_RESET"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_TASK_TIME = "extra_task_time"

        fun scheduleAlarmForTask(context: Context, task: Task) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            // 1. Schedule alarm for startTime
            val partsStart = task.startTime.split(":")
            if (partsStart.size == 2) {
                val hour = partsStart[0].toIntOrNull() ?: 0
                val minute = partsStart[1].toIntOrNull() ?: 0
                
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis < System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_TASK_REMINDER
                    putExtra(EXTRA_TASK_ID, task.id)
                    putExtra(EXTRA_TASK_NAME, task.name)
                    putExtra(EXTRA_TASK_TIME, task.startTime)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    task.id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    }
                } catch (e: SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } catch (t: Throwable) {
                    Log.e("AlarmReceiver", "Failed to schedule exact alarm for startTime", t)
                }
            }

            // 2. Schedule alarm for endTime (for auto-cross and notification)
            val partsEnd = task.endTime.split(":")
            if (partsEnd.size == 2) {
                val hour = partsEnd[0].toIntOrNull() ?: 0
                val minute = partsEnd[1].toIntOrNull() ?: 0
                
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis < System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_TASK_END_REMINDER
                    putExtra(EXTRA_TASK_ID, task.id)
                    putExtra(EXTRA_TASK_NAME, task.name)
                    putExtra(EXTRA_TASK_TIME, task.endTime)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    task.id + 10000, // Offset request code for end reminder
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    }
                } catch (e: SecurityException) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } catch (t: Throwable) {
                    Log.e("AlarmReceiver", "Failed to schedule exact alarm for endTime", t)
                }
            }
        }

        fun scheduleDailyReset(context: Context, lastTaskEndTime: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val parts = lastTaskEndTime.split(":")
            if (parts.size != 2) return
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return
            
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 5) // Reset daily helper exactly 5 minutes after last task's End Time
                
                if (timeInMillis < System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_DAILY_RESET
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9999, // Reserved daily reset id
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } catch (t: Throwable) {
                Log.e("AlarmReceiver", "Failed to schedule daily reset timer", t)
            }
        }

        fun cancelAlarmForTask(context: Context, taskId: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TASK_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }

            val intentEnd = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TASK_END_REMINDER
            }
            val pendingIntentEnd = PendingIntent.getBroadcast(
                context,
                taskId + 10000,
                intentEnd,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntentEnd != null) {
                alarmManager.cancel(pendingIntentEnd)
                pendingIntentEnd.cancel()
            }
        }

        fun cancelDailyReset(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_DAILY_RESET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9999,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
