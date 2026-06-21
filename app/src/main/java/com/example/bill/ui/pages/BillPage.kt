package com.example.bill.ui.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bill.data.Bill
import com.example.bill.data.BillType
import com.example.bill.ui.components.AddBillDialog
import com.example.bill.ui.theme.ExpenseColor
import com.example.bill.ui.theme.FabColor
import com.example.bill.ui.theme.IncomeColor
import com.example.bill.ui.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun BillPage(viewModel: BillViewModel, modifier: Modifier = Modifier) {
    val allBills by viewModel.allBills.collectAsState()
    val monthSummary by viewModel.monthSummary.collectAsState()

    val expenseCats by viewModel.getCategoriesByType(BillType.EXPENSE)
        .collectAsState(initial = emptyList())
    val incomeCats by viewModel.getCategoriesByType(BillType.INCOME)
        .collectAsState(initial = emptyList())
    val expenseCatNames = expenseCats.map { it.name }
    val incomeCatNames = incomeCats.map { it.name }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINESE)
    val dateHeaderFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE)

    // Refresh month summary on first load
    LaunchedEffect(Unit) {
        viewModel.refreshMonthSummary()
    }

    // Group bills by day (descending order - recent first)
    val billsByDay = remember(allBills) {
        val cal = Calendar.getInstance()
        allBills.groupBy { bill ->
            cal.timeInMillis = bill.dateMillis
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }.toList()
            .sortedByDescending { (key, _) -> key }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = FabColor,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(FabColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加账单",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ===== Header: Month Summary =====
            item(key = "month_summary") {
                MonthSummaryCard(
                    monthSummary = monthSummary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // ===== Bills grouped by day =====
            if (billsByDay.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "暂无账单",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右下角 + 添加账单",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                billsByDay.forEach { (dayKey, billsOfDay) ->
                    // Parse date for header
                    val parts = dayKey.split("-")
                    val year = parts[0].toInt()
                    val month = parts[1].toInt()
                    val day = parts[2].toInt()
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                    }
                    val dateHeader = dateHeaderFormat.format(cal.time)

                    // Calculate daily total
                    val dayExpense = billsOfDay.filter { it.type == BillType.EXPENSE }.sumOf { it.amountInCents }
                    val dayIncome = billsOfDay.filter { it.type == BillType.INCOME }.sumOf { it.amountInCents }

                    // Date header
                    item(key = "header_$dayKey") {
                        DailyDateHeader(
                            dateText = dateHeader,
                            expense = dayExpense,
                            income = dayIncome,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Bill items for this day
                    items(billsOfDay, key = { "bill_${it.id}" }) { bill ->
                        BillItem(
                            bill = bill,
                            timeFormat = timeFormat,
                            onEdit = { editingBill = it },
                            onDelete = { viewModel.deleteBill(bill) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog && editingBill == null) {
        AddBillDialog(
            editBill = null,
            expenseCategoryNames = expenseCatNames,
            incomeCategoryNames = incomeCatNames,
            onDismiss = { showAddDialog = false },
            onSave = { _, type, category, amountInCents, dateMillis, note ->
                viewModel.addBill(type, category, amountInCents, dateMillis, note)
            }
        )
    }

    if (editingBill != null) {
        AddBillDialog(
            editBill = editingBill,
            expenseCategoryNames = expenseCatNames,
            incomeCategoryNames = incomeCatNames,
            onDismiss = { editingBill = null },
            onSave = { id, type, category, amountInCents, dateMillis, note ->
                viewModel.updateBill(id, type, category, amountInCents, dateMillis, note)
                editingBill = null
            }
        )
    }
}

@Composable
private fun MonthSummaryCard(monthSummary: com.example.bill.ui.viewmodel.MonthSummary, modifier: Modifier = Modifier) {
    val expense = monthSummary.totalExpense
    val income = monthSummary.totalIncome
    val balance = income - expense

    val dateFormat = SimpleDateFormat("yyyy年MM月", Locale.CHINESE)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dateFormat.format(Date(System.currentTimeMillis())),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Balance
            Text(
                text = "结余",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Text(
                text = String.format("%.2f", balance / 100.0),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (balance >= 0) IncomeColor else ExpenseColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Income & Expense row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("收入", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        text = String.format("%.2f", income / 100.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeColor
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("支出", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        text = String.format("%.2f", expense / 100.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyDateHeader(
    dateText: String,
    expense: Long,
    income: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (expense > 0) {
                Text(
                    text = "支出 ¥${String.format("%.2f", expense / 100.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseColor,
                    fontWeight = FontWeight.Medium
                )
            }
            if (income > 0) {
                Text(
                    text = "收入 ¥${String.format("%.2f", income / 100.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = IncomeColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BillItem(
    bill: Bill,
    timeFormat: SimpleDateFormat,
    onEdit: (Bill) -> Unit,
    onDelete: (Bill) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (bill.type == BillType.EXPENSE)
                            ExpenseColor.copy(alpha = 0.1f)
                        else
                            IncomeColor.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bill.category.first().toString(),
                    color = if (bill.type == BillType.EXPENSE) ExpenseColor else IncomeColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bill.category,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = timeFormat.format(Date(bill.dateMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Note indicator
            if (bill.note.isNotBlank()) {
                Text(
                    text = bill.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Amount
            Text(
                text = "${if (bill.type == BillType.EXPENSE) "-" else "+"}${String.format("%.2f", bill.amountInCents / 100.0)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (bill.type == BillType.EXPENSE) ExpenseColor else IncomeColor
            )

            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("修改") },
                    onClick = {
                        showMenu = false
                        onEdit(bill)
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除", color = ExpenseColor) },
                    onClick = {
                        showMenu = false
                        onDelete(bill)
                    }
                )
            }
        }
    }
}