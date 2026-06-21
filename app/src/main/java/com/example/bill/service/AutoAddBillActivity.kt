package com.example.bill.service

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bill.data.BillType
import com.example.bill.ui.theme.ExpenseColor
import com.example.bill.ui.theme.IncomeColor
import com.example.bill.ui.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.emptyList

class AutoAddBillActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val amountInCents = intent.getLongExtra("amountInCents", 0L)
        val merchant = intent.getStringExtra("merchant") ?: ""
        val billTypeName = intent.getStringExtra("billType") ?: BillType.EXPENSE.name
        val defaultCategory = intent.getStringExtra("defaultCategory") ?: "其他"
        val appName = intent.getStringExtra("appName") ?: ""

        if (amountInCents < 0) {
            Toast.makeText(this, "无法识别金额", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Delay a bit to show the dialog after the notification appears
        Handler(Looper.getMainLooper()).postDelayed({
            setContent {
                AutoAddDialog(
                    amountInCents = amountInCents,
                    merchant = merchant,
                    billType = BillType.valueOf(billTypeName),
                    defaultCategory = defaultCategory,
                    appName = appName,
                    onDismiss = { finish() },
                    onSaved = { finish() }
                )
            }
        }, 500)
    }

    @Composable
    private fun AutoAddDialog(
        amountInCents: Long,
        merchant: String,
        billType: BillType,
        defaultCategory: String,
        appName: String,
        onDismiss: () -> Unit,
        onSaved: () -> Unit,
        viewModel: BillViewModel = viewModel()
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINESE)

        var currentAmountText by remember {
            mutableStateOf(String.format("%.2f", amountInCents / 100.0))
        }
        var currentCategory by remember { mutableStateOf(defaultCategory) }
        var currentNote by remember { mutableStateOf("$appName - $merchant") }
        var currentBillType by remember { mutableStateOf(billType) }
        var amountError by remember { mutableStateOf<String?>(null) }

        val expenseCats by viewModel.getCategoriesByType(BillType.EXPENSE)
            .collectAsState(initial = emptyList())
        val incomeCats by viewModel.getCategoriesByType(BillType.INCOME)
            .collectAsState(initial = emptyList())

        val categories = if (currentBillType == BillType.EXPENSE)
            expenseCats.map { it.name } else incomeCats.map { it.name }

        // Ensure currentCategory is in the list
        if (categories.isNotEmpty() && categories.none { it == currentCategory }) {
            currentCategory = categories[0]
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text("添加账单", fontWeight = FontWeight.Bold)
                    Text(
                        text = dateFormat.format(Date()),
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Type selector
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentBillType = BillType.EXPENSE },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                if (currentBillType == BillType.EXPENSE) ExpenseColor else androidx.compose.ui.graphics.Color.Gray
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (currentBillType == BillType.EXPENSE) ExpenseColor else androidx.compose.ui.graphics.Color.Gray
                            )
                        ) {
                            Text("支出", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { currentBillType = BillType.INCOME },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                if (currentBillType == BillType.INCOME) IncomeColor else androidx.compose.ui.graphics.Color.Gray
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (currentBillType == BillType.INCOME) IncomeColor else androidx.compose.ui.graphics.Color.Gray
                            )
                        ) {
                            Text("收入", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Merchant info
                    Text(
                        text = "来自：$appName",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Amount field (pre-filled, editable)
                    Text("金额", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentAmountText,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                currentAmountText = value
                                amountError = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        suffix = { Text("元") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = amountError != null,
                        supportingText = amountError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category
                    Text("类别", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (categories.isNotEmpty()) {
                        val chunked = categories.chunked(3)
                        chunked.forEach { rowCats ->
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                            ) {
                                rowCats.forEach { cat ->
                                    val isSelected = currentCategory == cat
                                    val typeColor = if (currentBillType == BillType.EXPENSE) ExpenseColor else IncomeColor
                                    androidx.compose.material3.FilterChip(
                                        selected = isSelected,
                                        onClick = { currentCategory = cat },
                                        label = { Text(cat) },
                                        modifier = Modifier.weight(1f),
                                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = typeColor.copy(alpha = 0.15f),
                                            selectedLabelColor = typeColor
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    } else {
                        Text("暂无类别", color = androidx.compose.ui.graphics.Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Note
                    Text("备注", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = currentNote,
                        onValueChange = { currentNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    // Save & Exit
                    Button(
                        onClick = {
                            if (doSave(viewModel, currentAmountText, currentBillType, currentCategory, currentNote)) {
                                onSaved()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentBillType == BillType.EXPENSE) ExpenseColor else IncomeColor
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存并退出", fontWeight = FontWeight.Bold)
                    }

                    // Save & Continue
                    OutlinedButton(
                        onClick = {
                            if (doSave(viewModel, currentAmountText, currentBillType, currentCategory, currentNote)) {
                                // Reset form for next entry
                                currentAmountText = ""
                                currentNote = "$appName - $merchant"
                                amountError = null
                                Toast.makeText(this@AutoAddBillActivity, "已保存，继续添加...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (currentBillType == BillType.EXPENSE) ExpenseColor else IncomeColor
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (currentBillType == BillType.EXPENSE) ExpenseColor else IncomeColor
                        )
                    ) {
                        Text("保存并继续", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }

    private fun doSave(
        viewModel: BillViewModel,
        amountText: String,
        type: BillType,
        category: String,
        note: String
    ): Boolean {
        if (amountText.isBlank()) return false
        val amountDouble = amountText.toDoubleOrNull()
        if (amountDouble == null || amountDouble < 0) return false

        val finalAmount = (amountDouble * 100 + 0.5).toLong()
        viewModel.addBill(
            type = type,
            category = category,
            amountInCents = finalAmount,
            dateMillis = System.currentTimeMillis(),
            note = note
        )
        return true
    }
}