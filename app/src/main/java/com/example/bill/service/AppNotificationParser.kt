package com.example.bill.service

import android.service.notification.StatusBarNotification
import android.os.Bundle
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

interface AppNotificationParser {
    fun parse(sbn: StatusBarNotification): ParsedNotification?
}

// ====== Alipay ======
object AlipayParser : AppNotificationParser {
    override fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val text = getNotificationText(sbn) ?: return null
        if (!hasAmountIndicator(text)) return null
        if (!hasPaymentKeyword(text)) return null

        val amount = AmountExtractor.extract(text) ?: return null

        return ParsedNotification(
            amountInCents = amount,
            merchant = "支付宝",
            billType = BillType.EXPENSE,
            defaultCategory = "餐饮",
            appName = "支付宝"
        )
    }
}

// ====== Meituan ======
object MeituanParser : AppNotificationParser {
    override fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val text = getNotificationText(sbn) ?: return null
        if (!hasAmountIndicator(text)) return null
        if (!hasPaymentKeyword(text)) return null

        val amount = AmountExtractor.extract(text) ?: return null
        val category = when {
            text.contains("外卖") -> "餐饮"
            text.contains("到店") || text.contains("买单") -> "餐饮"
            text.contains("酒店") || text.contains("旅行") -> "住房"
            text.contains("电影") || text.contains("娱乐") -> "娱乐"
            else -> "餐饮"
        }

        return ParsedNotification(
            amountInCents = amount,
            merchant = "美团",
            billType = BillType.EXPENSE,
            defaultCategory = category,
            appName = "美团"
        )
    }
}

// ====== Douyin (TikTok) ======
object DouyinParser : AppNotificationParser {
    override fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val text = getNotificationText(sbn) ?: return null
        if (!hasAmountIndicator(text)) return null
        if (!hasPaymentKeyword(text)) return null

        val amount = AmountExtractor.extract(text) ?: return null

        return ParsedNotification(
            amountInCents = amount,
            merchant = "抖音",
            billType = BillType.EXPENSE,
            defaultCategory = "娱乐",
            appName = "抖音"
        )
    }
}

// ====== JD ======
object JDParser : AppNotificationParser {
    override fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val text = getNotificationText(sbn) ?: return null
        if (!hasAmountIndicator(text)) return null
        if (!hasPaymentKeyword(text)) return null

        val amount = AmountExtractor.extract(text) ?: return null

        return ParsedNotification(
            amountInCents = amount,
            merchant = "京东",
            billType = BillType.EXPENSE,
            defaultCategory = "购物",
            appName = "京东"
        )
    }
}

// ====== Taobao ======
object TaobaoParser : AppNotificationParser {
    override fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val text = getNotificationText(sbn) ?: return null
        if (!hasAmountIndicator(text)) return null
        if (!hasPaymentKeyword(text)) return null

        val amount = AmountExtractor.extract(text) ?: return null

        return ParsedNotification(
            amountInCents = amount,
            merchant = "淘宝",
            billType = BillType.EXPENSE,
            defaultCategory = "购物",
            appName = "淘宝"
        )
    }
}

// ====== Pinduoduo ======
object PddParser : AppNotificationParser {
    override fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val text = getNotificationText(sbn) ?: return null
        if (!hasAmountIndicator(text)) return null
        if (!hasPaymentKeyword(text)) return null

        val amount = AmountExtractor.extract(text) ?: return null

        return ParsedNotification(
            amountInCents = amount,
            merchant = "拼多多",
            billType = BillType.EXPENSE,
            defaultCategory = "购物",
            appName = "拼多多"
        )
    }
}

// ====== WeChat ======
object WechatParser : AppNotificationParser {
    override fun parse(sbn: StatusBarNotification): ParsedNotification? {
        val text = getNotificationText(sbn) ?: return null
        if (!hasAmountIndicator(text)) return null
        if (!hasPaymentKeyword(text)) return null

        val amount = AmountExtractor.extract(text) ?: return null

        return ParsedNotification(
            amountInCents = amount,
            merchant = "微信支付",
            billType = BillType.EXPENSE,
            defaultCategory = "餐饮",
            appName = "微信"
        )
    }
}

// ====== App matcher ======
object AppMatcher {
    data class AppInfo(
        val packageName: String,
        val parser: AppNotificationParser
    )

    private val appList = listOf(
        AppInfo("com.eg.android.AlipayGphone", AlipayParser),
        AppInfo("com.sankuai.meituan", MeituanParser),
        AppInfo("com.ss.android.ugc.aweme", DouyinParser),
        AppInfo("com.jingdong.app.mail", JDParser),
        AppInfo("com.taobao.taobao", TaobaoParser),
        AppInfo("com.xunmeng.pinduoduo", PddParser),
        AppInfo("com.tencent.mm", WechatParser),
    )

    fun findParser(packageName: String): AppNotificationParser? {
        return appList.firstOrNull { it.packageName == packageName }?.parser
    }
}

/**
 * Check if text likely contains a money amount (has ¥, ￥, or 元).
 */
private fun hasAmountIndicator(text: String): Boolean =
    text.contains("¥") || text.contains("￥") || text.contains("元") || text.contains("金额")

// ====== Helpers ======
private fun getNotificationText(sbn: StatusBarNotification): String? {
    val extras: Bundle = sbn.notification.extras ?: return null
    // Try multiple possible keys for text content
    val text = extras.getString(NotificationCompat.EXTRA_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_SUB_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_BIG_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_INFO_TEXT)
        ?: extras.getString(NotificationCompat.EXTRA_SUMMARY_TEXT)
    return text?.trim()
}

private fun getNotificationTitle(sbn: StatusBarNotification): String? {
    val extras: Bundle = sbn.notification.extras ?: return null
    return extras.getString(NotificationCompat.EXTRA_TITLE)?.trim()
}

private val PAYMENT_KEYWORDS = listOf("成功支付", "支付成功", "成功付款", "付款成功")

private fun hasPaymentKeyword(text: String): Boolean =
    PAYMENT_KEYWORDS.any { text.contains(it) }
