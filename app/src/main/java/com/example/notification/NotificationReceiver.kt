package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.NotificationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(NotificationHelper.EXTRA_TASK_ID, 0)
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE) ?: "Lembrete de Tarefa"
        val message = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "Sua rotina está em andamento"
        val isVibrationOnly = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_VIBRATION_ONLY, false)
        val alertType = intent.getIntExtra(NotificationHelper.EXTRA_ALERT_TYPE, 1)

        val channelId = if (isVibrationOnly) {
            NotificationHelper.CHANNEL_ID_VIBRATION
        } else {
            NotificationHelper.CHANNEL_ID_SOUND
        }

        // Garante criação dos canais
        NotificationHelper.createNotificationChannels(context)

        val notificationId = (taskId * 10) + alertType

        // Ao tocar na notificação, abre o app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Remove alertas anteriores da mesma tarefa (ex: remove aviso prévio ao iniciar a tarefa)
        val alertTypes = listOf(
            NotificationHelper.TYPE_START_EXACT,
            NotificationHelper.TYPE_START_EARLY,
            NotificationHelper.TYPE_END_EXACT,
            NotificationHelper.TYPE_END_EARLY
        )
        for (type in alertTypes) {
            val oldId = (taskId * 10) + type
            if (oldId != notificationId) {
                notificationManager.cancel(oldId)
            }
        }

        // 2. Mantém apenas as 2 notificações mais recentes na bandeja do sistema
        trimToMaxActiveNotifications(notificationManager, currentNotificationId = notificationId, maxAllowed = 2)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60 * 60 * 1000L) // Auto-expiração nativa em 2 horas
            .setContentIntent(pendingContentIntent)
            .setDefaults(
                if (isVibrationOnly) NotificationCompat.DEFAULT_VIBRATE
                else NotificationCompat.DEFAULT_ALL
            )

        notificationManager.notify(notificationId, builder.build())

        // Executa vibração explícita para garantir em aparelhos onde o canal silencioso não vibra
        if (isVibrationOnly) {
            triggerVibration(context)
        }

        // CRÍTICO: Reagenda a tarefa para o dia seguinte (garante repetição diária ininterrupta)
        if (taskId != 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val task = db.taskDao().getTaskById(taskId)
                    if (task != null && task.isEnabled && task.notificationMode != NotificationMode.OFF) {
                        NotificationHelper.scheduleTaskAlerts(context, task)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val pattern = longArrayOf(0, 350, 150, 350)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            // Ignora se o dispositivo não suportar vibração
        }
    }

    /**
     * Mantém apenas as notificações mais recentes na barra de status, descartando as mais antigas
     * para que o total de notificações ativas do app nunca ultrapasse [maxAllowed].
     */
    private fun trimToMaxActiveNotifications(
        notificationManager: NotificationManager,
        currentNotificationId: Int,
        maxAllowed: Int
    ) {
        try {
            val active = notificationManager.activeNotifications ?: return
            // Filtra as notificações ativas que não são a atual que está sendo disparada/atualizada
            val others = active.filter { it.id != currentNotificationId }
            val maxAllowedOthers = (maxAllowed - 1).coerceAtLeast(0)
            if (others.size > maxAllowedOthers) {
                // Ordena pelas mais antigas com base no horário de postagem
                val sortedOldest = others.sortedBy { it.postTime }
                val countToRemove = others.size - maxAllowedOthers
                for (i in 0 until countToRemove) {
                    notificationManager.cancel(sortedOldest[i].tag, sortedOldest[i].id)
                }
            }
        } catch (e: Exception) {
            // Previne exceções em versões customizadas de fabricantes
            e.printStackTrace()
        }
    }
}
