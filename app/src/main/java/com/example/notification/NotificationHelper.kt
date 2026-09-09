package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.NotificationMode
import com.example.data.model.RoutineTask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationHelper {
    const val CHANNEL_ID_SOUND = "routine_tasks_sound"
    const val CHANNEL_ID_VIBRATION = "routine_tasks_vibration"

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_ICON = "extra_task_icon"
    const val EXTRA_IS_VIBRATION_ONLY = "extra_is_vibration_only"
    const val EXTRA_ALERT_TYPE = "extra_alert_type"
    const val EXTRA_MESSAGE = "extra_message"

    const val TYPE_START_EXACT = 1
    const val TYPE_START_EARLY = 2
    const val TYPE_END_EXACT = 3
    const val TYPE_END_EARLY = 4

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal 1: Som e Vibração
            val soundChannel = NotificationChannel(
                CHANNEL_ID_SOUND,
                "Lembretes de Rotina (Com Som)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações sonoras e com vibração dos horários das tarefas"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Canal 2: Apenas Vibração (Silencioso sem som de áudio)
            val vibrationChannel = NotificationChannel(
                CHANNEL_ID_VIBRATION,
                "Lembretes de Rotina (Apenas Vibração)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações com vibração discreta sem alerta sonoro"
                enableVibration(true)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(soundChannel)
            notificationManager.createNotificationChannel(vibrationChannel)
        }
    }

    fun scheduleTaskAlerts(context: Context, task: RoutineTask) {
        // Primeiro, limpa agendamentos anteriores da tarefa
        cancelTaskAlerts(context, task.id)

        if (!task.isEnabled || task.notificationMode == NotificationMode.OFF) {
            return
        }

        val isVibrationOnly = task.notificationMode == NotificationMode.VIBRATION

        // 1. Alerta de Início
        if (task.notifyAtStart) {
            // Se houver aviso prévio (ex: 5 min antes)
            if (task.startAdvanceMinutes > 0) {
                val triggerMin = (task.startMinute - task.startAdvanceMinutes + 1440) % 1440
                scheduleSingleAlarm(
                    context = context,
                    taskId = task.id,
                    alertType = TYPE_START_EARLY,
                    minuteOfDay = triggerMin,
                    title = "${task.icon} Em ${task.startAdvanceMinutes} min: ${task.title}",
                    message = "Sua tarefa começará em breve (${task.formatStartTime()})",
                    icon = task.icon,
                    isVibrationOnly = isVibrationOnly
                )
            }

            // Alerta na hora exata do início
            scheduleSingleAlarm(
                context = context,
                taskId = task.id,
                alertType = TYPE_START_EXACT,
                minuteOfDay = task.startMinute,
                title = "${task.icon} Hora de: ${task.title}",
                message = "Iniciando agora (${task.formatTimeRange()})",
                icon = task.icon,
                isVibrationOnly = isVibrationOnly
            )
        }

        // 2. Alerta de Término
        if (task.notifyAtEnd) {
            val endMin = (task.startMinute + task.durationMinutes) % 1440

            // Se houver aviso prévio de término (ex: 5 min antes de acabar)
            if (task.endAdvanceMinutes > 0) {
                val triggerMin = (endMin - task.endAdvanceMinutes + 1440) % 1440
                scheduleSingleAlarm(
                    context = context,
                    taskId = task.id,
                    alertType = TYPE_END_EARLY,
                    minuteOfDay = triggerMin,
                    title = "${task.icon} Quase no fim: ${task.title}",
                    message = "Faltam ${task.endAdvanceMinutes} min para encerrar",
                    icon = task.icon,
                    isVibrationOnly = isVibrationOnly
                )
            } else {
                // Alerta no término exato
                scheduleSingleAlarm(
                    context = context,
                    taskId = task.id,
                    alertType = TYPE_END_EXACT,
                    minuteOfDay = endMin,
                    title = "${task.icon} Concluído: ${task.title}",
                    message = "Horário de encerramento da tarefa",
                    icon = task.icon,
                    isVibrationOnly = isVibrationOnly
                )
            }
        }
    }

    private fun scheduleSingleAlarm(
        context: Context,
        taskId: Int,
        alertType: Int,
        minuteOfDay: Int,
        title: String,
        message: String,
        icon: String,
        isVibrationOnly: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_TASK_ICON, icon)
            putExtra(EXTRA_IS_VIBRATION_ONLY, isVibrationOnly)
            putExtra(EXTRA_ALERT_TYPE, alertType)
        }

        val requestCode = (taskId * 10) + alertType
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerEpochMillis = calculateNextTriggerMillis(minuteOfDay)

        // Intent de exibição quando o usuário clica no despertador do sistema
        val showIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Se o sistema suporta AlarmClockInfo (API 21+), usamos setAlarmClock que tem a
            // prioridade máxima do sistema Android, acordando o dispositivo no segundo exato
            // mesmo no sono profundo (Doze Mode) e com a tela apagada.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // No Android 12+, se tiver a API canScheduleExactAlarms(), podemos checar antes
                val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

                if (canScheduleExact) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerEpochMillis, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback caso falte permissão explícita em versões específicas
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                }
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelTaskAlerts(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val alertTypes = listOf(TYPE_START_EXACT, TYPE_START_EARLY, TYPE_END_EXACT, TYPE_END_EARLY)
        for (alertType in alertTypes) {
            val intent = Intent(context, NotificationReceiver::class.java)
            val requestCode = (taskId * 10) + alertType
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun calculateNextTriggerMillis(minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60, 0)
        var targetDateTime = LocalDateTime.of(now.toLocalDate(), targetTime)

        // Se já passou do horário no dia de hoje (ou dentro dos últimos 20 segundos), agenda para o próximo dia
        if (!targetDateTime.isAfter(now.plusSeconds(5))) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        return targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
