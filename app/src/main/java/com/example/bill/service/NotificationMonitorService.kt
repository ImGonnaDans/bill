package com.example.bill.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bill.MainActivity
import java.util.Locale

class NotificationMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifMonitor"
        private const val PREFS_NAME = "auto_add_prefs"
        private const val KEY_DELAY_SECONDS = "auto_add_delay_seconds"
        private const val DEFAULT_DELAY = 5

        private const val FOREGROUND_CHANNEL_ID = "listener_service"
        private const val NOTIF_CHANNEL_ID = "bill_detected"
        private const val NOTIF_CHANNEL_NAME = "账单提醒"
        private const val FOREGROUND_NOTIF_ID = 1001
        private var notifCounter = 1002

        fun getDelaySeconds(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_DELAY_SECONDS, DEFAULT_DELAY)
        }

        fun setDelaySeconds(context: Context, seconds: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_DELAY_SECONDS, seconds.coerceIn(1, 30)).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForegroundService()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val parser = AppMatcher.findParser(packageName) ?: return

        val parsed = parser.parse(sbn) ?: return

        if (parsed.amountInCents < 0) return

        // Send a notification with fullScreenIntent to auto-open the activity
        // (fullScreenIntent works from background on Android 14+, while startActivity is silently blocked)
        sendBillDetectedNotification(parsed)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op
    }

    private fun createNotificationChannels() {
        val fgChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "监听服务",
            NotificationManager.IMPORTANCE_MIN
        )
        val billChannel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            NOTIF_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(fgChannel)
        manager.createNotificationChannel(billChannel)
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("记账本")
            .setContentText("正在监听支付通知...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        startForeground(FOREGROUND_NOTIF_ID, notification)
    }

    private fun sendBillDetectedNotification(parsed: ParsedNotification) {
        val typeLabel = if (parsed.billType == com.example.bill.data.BillType.EXPENSE) "支出" else "收入"
        val amountStr = String.format(Locale.US, "%.2f", parsed.amountInCents / 100.0)

        val openIntent = Intent(this, AutoAddBillActivity::class.java).apply {
            putExtra("amountInCents", parsed.amountInCents)
            putExtra("merchant", parsed.merchant)
            putExtra("billType", parsed.billType.name)
            putExtra("defaultCategory", parsed.defaultCategory)
            putExtra("appName", parsed.appName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("发现${parsed.appName}${typeLabel}：¥$amountStr")
            .setContentText("${parsed.merchant} - 添加账单")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notifCounter++, notification)
    }
}
