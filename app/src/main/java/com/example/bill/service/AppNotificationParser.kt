package com.example.bill.service

import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.bill.data.BillType
import java.util.regex.Pattern

/**
 * Extracts amount from notification text.
 * Supports: ¥12.50, ￥12.50, 12.50元, 12.5元, 金额:12.50
 */
object AmountExtractor {
    private val patterns = listOf(
        Pattern.compile("""[¥￥]\s*(\d+(?:\.\d{1,2})?)"""),
        Pattern.compile("""(\d+\.\d{1,2})\s*元"""),
        Pattern.compile("""(\d+)\s*元"""),
        Pattern.compile("""金额[：:]?\s*(\d+(?:\.\d{1,2})?)"""),
        Pattern.compile("""(\d+(?:\.\d{1,2})?)\s*元""")
    )

    fun extract(text: String): Long? {
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amount = matcher.group(1)?.toDoubleOrNull() ?: continue
                return (amount * 100 + 0.5).toLong()
            }
        }
        return null
    }
}

// Built-in payment keywords (regex patterns)
private val BUILTIN_PATTERNS = listOf(
    Pattern.compile("成功支付"),
    Pattern.compile("支付成功"),
    Pattern.compile("成功付款"),
    Pattern.compile("付款成功"),
    Pattern.compile("你有一笔.*?元的支出")
)

private fun hasBuiltinPaymentKeyword(text: String): Boolean =
    BUILTIN_PATTERNS.any { it.matcher(text).find() }

/**
 * Check if text has a money amount indicator.
 */
fun hasAmountIndicator(text: String): Boolean =
    text.contains("¥") || text.contains("￥") || text.contains("元") || text.contains("金额")

/**
 * Parse a notification text using built-in patterns only.
 * Returns ParsedNotification if a payment keyword + amount is found.
 */
fun parseNotification(text: String, appName: String = "其他"): ParsedNotification? {
    if (!hasAmountIndicator(text)) return null
    if (!hasBuiltinPaymentKeyword(text)) return null

    val amount = AmountExtractor.extract(text) ?: return null

    return ParsedNotification(
        amountInCents = amount,
        merchant = appName,
        billType = BillType.EXPENSE,
        defaultCategory = "餐饮",
        appName = appName
    )
}

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