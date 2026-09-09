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

        // Ao tocar na notificação, abre o app
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            taskId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = (taskId * 10) + alertType

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingContentIntent)
            .setDefaults(
                if (isVibrationOnly) NotificationCompat.DEFAULT_VIBRATE
                else NotificationCompat.DEFAULT_ALL
            )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())

        // Executa vibração explícita para garantir em aparelhos onde o canal silencioso não vibra
        if (isVibrationOnly) {
            triggerVibration(context)
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
}
