package com.example.bill.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bill.data.BillType
import com.example.bill.data.CategoryTotal
import com.example.bill.data.DailyTotal
import com.example.bill.ui.components.LineChart
import com.example.bill.ui.components.PieChart
import com.example.bill.ui.theme.BalanceColor
import com.example.bill.ui.theme.ExpenseColor
import com.example.bill.ui.theme.IncomeColor
import com.example.bill.ui.viewmodel.BillViewModel
import com.example.bill.ui.viewmodel.StatsMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPage(viewModel: BillViewModel, modifier: Modifier = Modifier) {
    val statsMode by viewModel.statsMode.collectAsState()
    val statsYear by viewModel.statsYear.collectAsState()
    val statsMonth by viewModel.statsMonth.collectAsState()
    val statsStart by viewModel.statsStartMillis.collectAsState()
    val statsEnd by viewModel.statsEndMillis.collectAsState()
    val selectedStatsType by viewModel.selectedStatsType.collectAsState()

    val expenseTotal by viewModel.statsExpenseTotal.collectAsState()
    val incomeTotal by viewModel.statsIncomeTotal.collectAsState()
    val statsCount by viewModel.statsCount.collectAsState()
    val statsDailyAvg by viewModel.statsDailyAvg.collectAsState()
    val categoryBreakdown by viewModel.statsCategoryBreakdown.collectAsState()

    val pagerState = rememberPagerState(
        initialPage = 1, // Start at MONTH tab
        pageCount = { 3 }
    )
    val scope = rememberCoroutineScope()

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
    val yearMonthFormat = SimpleDateFormat("yyyy年", Locale.CHINESE)
    val monthFormat = SimpleDateFormat("yyyy年M月", Locale.CHINESE)

    // Load data initially and when mode changes
    LaunchedEffect(statsMode, statsStart, statsEnd) {
        viewModel.loadStatsData()
    }

    // Recalculate category totals and daily totals from breakdown data
    val categoryTotals = remember(categoryBreakdown) {
        categoryBreakdown.map { CategoryTotal(category = it.category, total = it.total) }
    }

    // Compute daily totals from the breakdown for the line chart (we need to use the raw flow)
    // For now, we'll compute a placeholder - the LineChart needs DailyTotal data
    // We'll get dailyTotals from the ViewModel's categoryTotalsByType flow (but we need DailyTotals)
    // Let's use the viewModel's existing method - it should be reactive

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "统计",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ===== Tab Selector (Year / Month / Custom) =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("按年", "按月", "自定义").forEachIndexed { index, label ->
                val isSelected = when (index) {
                    0 -> statsMode == StatsMode.YEAR
                    1 -> statsMode == StatsMode.MONTH
                    2 -> statsMode == StatsMode.CUSTOM
                    else -> false
                }
                OutlinedButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                        viewModel.setStatsMode(
                            when (index) {
                                0 -> StatsMode.YEAR
                                1 -> StatsMode.MONTH
                                else -> StatsMode.CUSTOM
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== Time Navigation =====
        when (statsMode) {
            StatsMode.YEAR -> {
                YearNavigator(
                    year = statsYear,
                    startDate = dateFormat.format(Date(statsStart)),
                    endDate = dateFormat.format(Date(statsEnd)),
                    onPrev = { viewModel.decreaseYear() },
                    onNext = { viewModel.increaseYear() }
                )
            }
            StatsMode.MONTH -> {
                MonthNavigator(
                    year = statsYear,
                    month = statsMonth,
                    startDate = dateFormat.format(Date(statsStart)),
                    endDate = dateFormat.format(Date(statsEnd)),
                    onPrev = { viewModel.decreaseMonth() },
                    onNext = { viewModel.increaseMonth() }
                )
            }
            StatsMode.CUSTOM -> {
                CustomDateSelector(
                    startMillis = statsStart,
                    endMillis = statsEnd,
                    onRangeChanged = { start, end -> viewModel.setCustomRange(start, end) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== Type Selector Buttons with amounts =====
        StatsTypeButtonRow(
            expenseTotal = expenseTotal,
            incomeTotal = incomeTotal,
            balance = incomeTotal - expenseTotal,
            selectedType = selectedStatsType,
            onTypeSelected = { viewModel.setSelectedStatsType(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ===== Content Blocks based on selected type =====
        when (selectedStatsType) {
            BillType.EXPENSE -> {
                StatsContentBlocks(
                    type = BillType.EXPENSE,
                    total = expenseTotal,
                    count = statsCount,
                    dailyAvg = statsDailyAvg,
                    categoryBreakdown = categoryBreakdown,
                    categoryTotals = categoryTotals,
                    statsStart = statsStart,
                    statsEnd = statsEnd,
                    viewModel = viewModel
                )
            }
            BillType.INCOME -> {
                StatsContentBlocks(
                    type = BillType.INCOME,
                    total = incomeTotal,
                    count = statsCount,
                    dailyAvg = statsDailyAvg,
                    categoryBreakdown = categoryBreakdown,
                    categoryTotals = categoryTotals,
                    statsStart = statsStart,
                    statsEnd = statsEnd,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun YearNavigator(
    year: Int,
    startDate: String,
    endDate: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrev) {
                Text("<", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "${year}年",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            IconButton(onClick = onNext) {
                Text(">", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = "$startDate ~ $endDate",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
private fun MonthNavigator(
    year: Int,
    month: Int,
    startDate: String,
    endDate: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrev) {
                Text("<", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "${year}年${month}月",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            IconButton(onClick = onNext) {
                Text(">", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = "$startDate ~ $endDate",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateSelector(
    startMillis: Long,
    endMillis: Long,
    onRangeChanged: (Long, Long) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { showStartPicker = true },
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(dateFormat.format(Date(startMillis)))
        }
        Text(
            text = " ~ ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        OutlinedButton(
            onClick = { showEndPicker = true },
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(dateFormat.format(Date(endMillis)))
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { start ->
                        if (start <= endMillis) {
                            onRangeChanged(start, endMillis)
                        }
                    }
                    showStartPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { end ->
                        val cal = Calendar.getInstance().apply { timeInMillis = end }
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        onRangeChanged(startMillis, cal.timeInMillis)
                    }
                    showEndPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun StatsTypeButtonRow(
    expenseTotal: Long,
    incomeTotal: Long,
    balance: Long,
    selectedType: BillType,
    onTypeSelected: (BillType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Expense Button
        TypeAmountButton(
            label = "支出",
            amount = expenseTotal,
            color = ExpenseColor,
            isSelected = selectedType == BillType.EXPENSE,
            onClick = { onTypeSelected(BillType.EXPENSE) },
            modifier = Modifier.weight(1f)
        )

        // Income Button
        TypeAmountButton(
            label = "收入",
            amount = incomeTotal,
            color = IncomeColor,
            isSelected = selectedType == BillType.INCOME,
            onClick = { onTypeSelected(BillType.INCOME) },
            modifier = Modifier.weight(1f)
        )

        // Balance Button
        TypeAmountButton(
            label = "结余",
            amount = balance,
            color = BalanceColor,
            isSelected = false, // Balance is not a filterable type
            onClick = { /* No action, just display */ },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TypeAmountButton(
    label: String,
    amount: Long,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) color else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("%.2f", amount / 100.0),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (label == "结余") {
                    if (amount >= 0) IncomeColor else ExpenseColor
                } else color
            )
        }
    }
}

@Composable
private fun StatsContentBlocks(
    type: BillType,
    total: Long,
    count: Int,
    dailyAvg: Double,
    categoryBreakdown: List<com.example.bill.data.CategoryBreakdown>,
    categoryTotals: List<CategoryTotal>,
    statsStart: Long,
    statsEnd: Long,
    viewModel: BillViewModel
) {
    val typeLabel = if (type == BillType.EXPENSE) "支出" else "收入"
    val typeColor = if (type == BillType.EXPENSE) ExpenseColor else IncomeColor

    // Get daily totals for line chart
    val dailyTotals by viewModel.getDailyTotalsBetween(statsStart, statsEnd, type)
        .collectAsState(initial = emptyList())

    // Get category totals for pie chart
    val catTotals by viewModel.getCategoryTotalsBetween(statsStart, statsEnd, type)
        .collectAsState(initial = emptyList())

    // Use the flow-based category totals (which include ALL categories, not just top ones)
    val pieChartData = catTotals.ifEmpty { categoryTotals }

    val totalAmount = pieChartData.sumOf { it.total }

    // ===== Block 1: Data count + Daily average =====
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("数据条数", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("日均$typeLabel", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format("%.1f", total / 100.0 / ((statsEnd - statsStart) / 86400000 + 1).coerceAtLeast(1)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ===== Block 2: Line Chart =====
    Text(
        text = "整体${typeLabel}趋势",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = statsStart
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        LineChart(
            dailyTotals = dailyTotals,
            year = year,
            month = month
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ===== Block 3: Pie Chart =====
    Text(
        text = "${typeLabel}分类统计",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        PieChart(
            categoryTotals = pieChartData,
            totalAmount = totalAmount
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ===== Block 4: Category breakdown list =====
    val breakdownData = if (categoryBreakdown.isNotEmpty()) categoryBreakdown
    else pieChartData.map { com.example.bill.data.CategoryBreakdown(category = it.category, total = it.total, count = 0) }

    Text(
        text = "${typeLabel}分类明细",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (breakdownData.isEmpty()) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("分类", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.weight(1f))
                    Text("总金额", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
                    Text("笔数", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
                }
                HorizontalDivider()

                breakdownData.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format("%.2f", item.total / 100.0),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = typeColor,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "${item.count}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.width(50.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}