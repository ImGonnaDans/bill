package com.example.bill.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bill.data.BillType
import com.example.bill.ui.components.LineChart
import com.example.bill.ui.components.PieChart
import com.example.bill.ui.theme.BalanceColor
import com.example.bill.ui.theme.ExpenseColor
import com.example.bill.ui.theme.IncomeColor
import com.example.bill.ui.viewmodel.BillViewModel
import kotlinx.coroutines.flow.Flow
import com.example.bill.data.CategoryTotal
import com.example.bill.data.DailyTotal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPage(viewModel: BillViewModel, modifier: Modifier = Modifier) {
    val selectedStatsType by viewModel.selectedStatsType.collectAsState()
    val statsYear by viewModel.statsYear.collectAsState()
    val statsMonth by viewModel.statsMonth.collectAsState()
    val statsStart by viewModel.statsStartMillis.collectAsState()
    val statsEnd by viewModel.statsEndMillis.collectAsState()

    // Stats data flows
    val expenseTotal by viewModel.getTotalByTypeBetween(statsStart, statsEnd, BillType.EXPENSE)
        .collectAsState(initial = null)
    val incomeTotal by viewModel.getTotalByTypeBetween(statsStart, statsEnd, BillType.INCOME)
        .collectAsState(initial = null)

    val categoryTotals by viewModel.getCategoryTotalsBetween(statsStart, statsEnd, selectedStatsType)
        .collectAsState(initial = emptyList())
    val dailyTotals by viewModel.getDailyTotalsBetween(statsStart, statsEnd, selectedStatsType)
        .collectAsState(initial = emptyList())

    val expense = expenseTotal ?: 0L
    val income = incomeTotal ?: 0L
    val balance = income - expense

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Period selector - Year/Month
        Text(
            text = "统计",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Year-Month selector
        YearMonthSelector(
            year = statsYear,
            month = statsMonth,
            onYearMonthChanged = { y, m -> viewModel.setStatsPeriod(y, m) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Type switcher buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsTypeButton(
                text = "支出",
                isSelected = selectedStatsType == BillType.EXPENSE,
                color = ExpenseColor,
                onClick = { viewModel.setSelectedStatsType(BillType.EXPENSE) },
                modifier = Modifier.weight(1f)
            )
            StatsTypeButton(
                text = "收入",
                isSelected = selectedStatsType == BillType.INCOME,
                color = IncomeColor,
                onClick = { viewModel.setSelectedStatsType(BillType.INCOME) },
                modifier = Modifier.weight(1f)
            )
            StatsTypeButton(
                text = "结余",
                isSelected = false,
                color = BalanceColor,
                onClick = {
                    // Toggle to a special "balance" mode - just disable selection highlight
                    // We handle this differently
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary card
        if (selectedStatsType == BillType.EXPENSE || selectedStatsType == BillType.INCOME) {
            SummaryCard(
                expense = expense,
                income = income,
                type = selectedStatsType,
                total = if (selectedStatsType == BillType.EXPENSE) expense else income
            )
        } else {
            // Balance mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BalanceColor.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("结余", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format("%.2f", balance / 100.0),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) IncomeColor else ExpenseColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("收入", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                String.format("%.2f", income / 100.0),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = IncomeColor
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("支出", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                String.format("%.2f", expense / 100.0),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Pie chart section
        if (selectedStatsType == BillType.EXPENSE || selectedStatsType == BillType.INCOME) {
            val totalAmount = categoryTotals.sumOf { it.total }
            Text(
                text = "分类占比",
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
                    categoryTotals = categoryTotals,
                    totalAmount = totalAmount
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Line chart section
            Text(
                text = "每日趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LineChart(
                    dailyTotals = dailyTotals,
                    year = statsYear,
                    month = statsMonth
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearMonthSelector(
    year: Int,
    month: Int,
    onYearMonthChanged: (Int, Int) -> Unit
) {
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }

    val years = (2020..2030).toList()
    val months = (1..12).toList()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Year dropdown
        ExposedDropdownMenuBox(
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = !yearExpanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = "${year}年",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(
                expanded = yearExpanded,
                onDismissRequest = { yearExpanded = false }
            ) {
                years.forEach { y ->
                    DropdownMenuItem(
                        text = { Text("${y}年") },
                        onClick = {
                            onYearMonthChanged(y, month)
                            yearExpanded = false
                        }
                    )
                }
            }
        }

        // Month dropdown
        ExposedDropdownMenuBox(
            expanded = monthExpanded,
            onExpandedChange = { monthExpanded = !monthExpanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = "${month}月",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(
                expanded = monthExpanded,
                onDismissRequest = { monthExpanded = false }
            ) {
                months.forEach { m ->
                    DropdownMenuItem(
                        text = { Text("${m}月") },
                        onClick = {
                            onYearMonthChanged(year, m)
                            monthExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsTypeButton(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isSelected) color else Color.Gray
        ),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (isSelected) color else Color.Gray.copy(alpha = 0.5f)
        )
    ) {
        Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SummaryCard(
    expense: Long,
    income: Long,
    type: BillType,
    total: Long
) {
    val color = if (type == BillType.EXPENSE) ExpenseColor else IncomeColor
    val label = if (type == BillType.EXPENSE) "总支出" else "总收入"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("%.2f", total / 100.0),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
