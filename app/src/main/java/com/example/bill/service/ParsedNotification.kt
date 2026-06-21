package com.example.bill.service

import com.example.bill.data.BillType

data class ParsedNotification(
    val amountInCents: Long,
    val merchant: String = "",
    val billType: BillType = BillType.EXPENSE,
    val defaultCategory: String = "其他",
    val appName: String = ""
)