package com.example.bill.service

import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.util.regex.Pattern

/**
 * Try to match a custom pattern (user-defined) against the text.
 * User writes patterns where * represents the amount.
 * Example: "您有一笔*人民币的消费" →
 *   compiled to regex: Pattern.compile("您有一笔(\d+(?:\.\d{1,2})?)人民币的消费")
 */
fun matchCustomPattern(text: String, rawPattern: String): Long? {
    // Replace * with amount capture group
    val regexStr = rawPattern.replace("*", """(\d+(?:\.\d{1,2})?)""")
    val pattern = try {
        Pattern.compile(regexStr)
    } catch (_: Exception) {
        return null
    }
    val matcher = pattern.matcher(text)
    if (matcher.find()) {
        val amount = matcher.group(1)?.toDoubleOrNull() ?: return null
        return (amount * 100 + 0.5).toLong()
    }
    return null
}

// ====== Helpers ======
fun getNotificationText(sbn: StatusBarNotification): String? {
    val extras: Bundle = sbn.notification.extras ?: return null
    val text = extras.getString(NotificationCompat.EXTRA_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_SUB_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_BIG_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_INFO_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_SUMMARY_TEXT)
    return text?.trim()
}