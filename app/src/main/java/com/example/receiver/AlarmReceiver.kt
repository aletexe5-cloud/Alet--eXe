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
                            title = "Smart Schedule: $taskName",
                            text = "It's $taskTime! Time to perform your scheduled task.",
                            notificationId = taskId.coerceAtLeast(1)
                        )
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
                                title = "Schedule Review: $percent% Achieved!",
                                text = "Tasks: $completed / $total. Timetable has reset for tomorrow's performance progress.",
                                notificationId = 9999
                            )

                            // Reset database tasks
                            taskDao.resetAllTasks()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error processing alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, text: String, notificationId: Int) {
        val channelId = "timetable_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Timetable Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled tasks and performance updates"
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
        const val ACTION_DAILY_RESET = "com.example.receiver.ACTION_DAILY_RESET"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_TASK_TIME = "extra_task_time"

        fun scheduleAlarmForTask(context: Context, task: Task) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            
            val parts = task.targetTime.split(":")
            if (parts.size != 2) return
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return
            
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
                putExtra(EXTRA_TASK_TIME, task.targetTime)
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
                Log.d("AlarmReceiver", "Scheduled alarm for task ${task.id} (${task.name}) at ${task.targetTime}")
            } catch (e: SecurityException) {
                // If permission is not given yet, fall back to normal set
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                Log.w("AlarmReceiver", "Exact alarm security restriction. Scheduled standard alarm.", e)
            }
        }

        fun scheduleDailyReset(context: Context, lastTaskTime: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val parts = lastTaskTime.split(":")
            if (parts.size != 2) return
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return
            
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, 10) // exactly 10 minutes trailing
                
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
                Log.d("AlarmReceiver", "Scheduled daily reset helper exactly 10 minutes after last task $lastTaskTime (Reset at: ${Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis }.time})")
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                Log.w("AlarmReceiver", "Exact alarm security restriction. Reset scheduled standard.", e)
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
                Log.d("AlarmReceiver", "Cancelled alarm for task $taskId")
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
                Log.d("AlarmReceiver", "Cancelled daily reset alarm")
            }
        }
    }
}
