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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val selectedDateMillis by viewModel.selectedDateMillis.collectAsState()
    val bills by viewModel.billsForSelectedDate.collectAsState()

    val expenseCats by viewModel.getCategoriesByType(BillType.EXPENSE)
        .collectAsState(initial = emptyList())
    val incomeCats by viewModel.getCategoriesByType(BillType.INCOME)
        .collectAsState(initial = emptyList())
    val expenseCatNames = expenseCats.map { it.name }
    val incomeCatNames = incomeCats.map { it.name }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }

    val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)
    val dayFormat = SimpleDateFormat("EEEE", Locale.CHINESE)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINESE)

    // Calculate daily totals
    val dailyExpense = bills.filter { it.type == BillType.EXPENSE }.sumOf { it.amountInCents }
    val dailyIncome = bills.filter { it.type == BillType.INCOME }.sumOf { it.amountInCents }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Date navigation header
            DateNavigator(
                selectedDateMillis = selectedDateMillis,
                onDateChange = { viewModel.setSelectedDate(it) },
                dateFormat = dateFormat,
                dayFormat = dayFormat
            )

            // Daily summary
            DailySummary(dailyExpense = dailyExpense, dailyIncome = dailyIncome)

            Spacer(modifier = Modifier.height(8.dp))

            // Bill list
            if (bills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
                ) {
                    items(bills, key = { it.id }) { bill ->
                        BillItem(
                            bill = bill,
                            timeFormat = timeFormat,
                            onEdit = { editingBill = it },
                            onDelete = { viewModel.deleteBill(bill) }
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
private fun DateNavigator(
    selectedDateMillis: Long,
    onDateChange: (Long) -> Unit,
    dateFormat: SimpleDateFormat,
    dayFormat: SimpleDateFormat
) {
    val today = Calendar.getInstance()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                cal.add(Calendar.DAY_OF_MONTH, -1)
                onDateChange(cal.timeInMillis)
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前一天")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dateFormat.format(Date(selectedDateMillis)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dayFormat.format(Date(selectedDateMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                cal.add(Calendar.DAY_OF_MONTH, 1)
                // Don't allow future dates
                if (cal.timeInMillis <= today.timeInMillis) {
                    onDateChange(cal.timeInMillis)
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "后一天")
            }
        }

        // Today button
        if (selectedDateMillis < today.timeInMillis) {
            androidx.compose.material3.TextButton(onClick = {
                onDateChange(today.timeInMillis)
            }) {
                Text("回到今天", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DailySummary(dailyExpense: Long, dailyIncome: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("支出", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(
                    text = String.format("%.2f", dailyExpense / 100.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseColor
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("收入", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(
                    text = String.format("%.2f", dailyIncome / 100.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IncomeColor
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("结余", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(
                    text = String.format("%.2f", (dailyIncome - dailyExpense) / 100.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (dailyIncome >= dailyExpense) IncomeColor else ExpenseColor
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
    onDelete: (Bill) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
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
