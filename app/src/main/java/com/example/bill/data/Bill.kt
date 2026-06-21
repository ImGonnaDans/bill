package com.example.bill.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BillType {
    EXPENSE,
    INCOME
}

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: BillType,
    val category: String,
    val amountInCents: Long,
    val dateMillis: Long,
    val note: String = ""
)
