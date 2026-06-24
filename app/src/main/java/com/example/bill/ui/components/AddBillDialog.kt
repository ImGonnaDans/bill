package com.example.bill.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bill.data.Bill
import com.example.bill.data.BillType
import com.example.bill.ui.theme.ExpenseColor
import com.example.bill.ui.theme.IncomeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CategoryItem(val name: String, val icon: String)

val defaultCategoryEmojis = mapOf(
    "餐饮" to "🍽", "交通" to "🚗", "购物" to "🛍", "娱乐" to "🎮",
    "住房" to "🏠", "服饰" to "👔", "医疗" to "💊", "教育" to "📚",
    "薪资" to "💰", "理财" to "📈", "奖金" to "🏆", "兼职" to "💼",
    "红包" to "🧧", "经营" to "🏪", "其他" to "📦"
)

fun categoryDisplayName(name: String): String {
    val emoji = defaultCategoryEmojis[name] ?: "📌"
    return "$emoji $name"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddBillDialog(
    editBill: Bill? = null,
    expenseCategoryNames: List<String> = emptyList(),
    incomeCategoryNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Long, BillType, String, Long, Long, String) -> Unit
) {
    val defaultExpense = if (expenseCategoryNames.isNotEmpty()) expenseCategoryNames[0] else "其他"
    val defaultIncome = if (incomeCategoryNames.isNotEmpty()) incomeCategoryNames[0] else "其他"

    val initialType = editBill?.type ?: BillType.EXPENSE
    val initialCategory = editBill?.category
        ?: if (initialType == BillType.EXPENSE) defaultExpense else defaultIncome
    val initialAmount = editBill?.amountInCents?.let { String.format("%.2f", it / 100.0) } ?: ""
    val initialDate = editBill?.dateMillis ?: System.currentTimeMillis()
    val initialNote = editBill?.note ?: ""

    var billType by remember { mutableStateOf(initialType) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var amountText by remember { mutableStateOf(initialAmount) }
    var dateMillis by remember { mutableStateOf(initialDate) }
    var note by remember { mutableStateOf(initialNote) }
    var showDatePicker by remember { mutableStateOf(false) }

    var amountError by remember { mutableStateOf<String?>(null) }

    val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)

    val categories = if (billType == BillType.EXPENSE) expenseCategoryNames else incomeCategoryNames

    if (categories.isNotEmpty() && categories.none { it == selectedCategory }) {
        selectedCategory = categories[0]
    }

    val isEditing = editBill != null
    val dialogTitle = if (isEditing) "修改账单" else "添加账单"

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(dialogTitle, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { billType = BillType.EXPENSE },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            2.dp,
                            if (billType == BillType.EXPENSE) ExpenseColor else Color.Gray
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (billType == BillType.EXPENSE) ExpenseColor else Color.Gray
                        )
                    ) {
                        Text("支出", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { billType = BillType.INCOME },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            2.dp,
                            if (billType == BillType.INCOME) IncomeColor else Color.Gray
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (billType == BillType.INCOME) IncomeColor else Color.Gray
                        )
                    ) {
                        Text("收入", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("类别", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                if (categories.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(categoryDisplayName(cat)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (billType == BillType.EXPENSE)
                                        ExpenseColor.copy(alpha = 0.15f) else IncomeColor.copy(alpha = 0.15f),
                                    selectedLabelColor = if (billType == BillType.EXPENSE)
                                        ExpenseColor else IncomeColor
                                )
                            )
                        }
                    }
                } else {
                    Text("暂无类别", color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("金额", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = value
                            amountError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("0.00") },
                    suffix = { Text("元") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("日期", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(dateFormat.format(Date(dateMillis)))
                }

                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = dateMillis
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { dateMillis = it }
                                showDatePicker = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("备注（可选）", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("添加备注...") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (amountText.isBlank()) {
                        amountError = "请输入金额"
                        return@Button
                    }
                    val amountDouble = amountText.toDoubleOrNull()
                    if (amountDouble == null || amountDouble < 0) {
                        amountError = "请输入有效金额"
                        return@Button
                    }

                    val amountInCents = (amountDouble * 100 + 0.5).toLong()

                    onSave(editBill?.id ?: 0L, billType, selectedCategory, amountInCents, dateMillis, note)

                    if (isEditing) {
                        onDismiss()
                    } else {
                        amountText = ""
                        note = ""
                        amountError = null
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (billType == BillType.EXPENSE) ExpenseColor else IncomeColor
                )
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
