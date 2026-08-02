package com.example.bill.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

class NotificationMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifMonitor"
        private const val PREFS_NAME = "auto_add_prefs"
        private const val KEY_DELAY_SECONDS = "auto_add_delay_seconds"
        private const val KEY_CUSTOM_PATTERNS = "custom_patterns"
        private const val DEFAULT_DELAY = 5
        private val DEFAULT_CUSTOM_PATTERNS = listOf("你有一笔*元的支出")

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

        fun getCustomPatterns(context: Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_CUSTOM_PATTERNS)) {
                return DEFAULT_CUSTOM_PATTERNS
            }
            val json = prefs.getString(KEY_CUSTOM_PATTERNS, "[]") ?: "[]"
            return try {
                org.json.JSONArray(json).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun setCustomPatterns(context: Context, patterns: List<String>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = org.json.JSONArray(patterns).toString()
            prefs.edit().putString(KEY_CUSTOM_PATTERNS, json).apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForegroundService()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val text = getNotificationText(sbn) ?: return

        // Map package name to readable app name
        val appName = packageNameToAppName(sbn.packageName)

        var parsed: ParsedNotification? = null

        // Match against user-defined custom patterns
        val customPatterns = getCustomPatterns(this)
        for (pattern in customPatterns) {
            val amount = matchCustomPattern(text, pattern)
            if (amount != null && amount >= 0) {
                parsed = ParsedNotification(
                    amountInCents = amount,
                    merchant = appName,
                    billType = com.example.bill.data.BillType.EXPENSE,
                    defaultCategory = "餐饮",
                    appName = appName
                )
                break
            }
        }

        if (parsed == null || parsed.amountInCents < 0) return

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

    private fun packageNameToAppName(packageName: String): String = when (packageName) {
        "com.eg.android.AlipayGphone" -> "支付宝"
        "com.sankuai.meituan" -> "美团"
        "com.ss.android.ugc.aweme" -> "抖音"
        "com.jingdong.app.mail" -> "京东"
        "com.taobao.taobao" -> "淘宝"
        "com.xunmeng.pinduoduo" -> "拼多多"
        "com.tencent.mm" -> "微信"
        else -> packageName.substringAfterLast('.').take(6)
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