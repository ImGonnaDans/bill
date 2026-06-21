package com.example.bill.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.bill.ui.pages.BillPage
import com.example.bill.ui.pages.SettingsPage
import com.example.bill.ui.pages.StatsPage
import com.example.bill.ui.viewmodel.BillViewModel

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)

private val navItems = listOf(
    BottomNavItem("记账", Icons.Default.Home),
    BottomNavItem("统计", Icons.Default.DateRange),
    BottomNavItem("设置", Icons.Default.Settings)
)

@Composable
fun BillApp(viewModel: BillViewModel) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedIndex) {
            0 -> BillPage(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            1 -> StatsPage(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> SettingsPage(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
