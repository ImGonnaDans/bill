package com.example.bill.ui.pages

import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.bill.data.BillType
import com.example.bill.data.CategoryEntity
import com.example.bill.ui.components.categoryDisplayName
import com.example.bill.ui.theme.ExpenseColor
import com.example.bill.ui.theme.IncomeColor
import com.example.bill.ui.viewmodel.BillViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPage(
    viewModel: BillViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val processingState by viewModel.processingState.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val avatarUri by viewModel.avatarUri.collectAsState()
    val allBills by viewModel.allBills.collectAsState()

    val expenseCats by viewModel.getCategoriesByType(BillType.EXPENSE)
        .collectAsState(initial = emptyList())
    val incomeCats by viewModel.getCategoriesByType(BillType.INCOME)
        .collectAsState(initial = emptyList())

    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var categoriesExpanded by remember { mutableStateOf(false) }
    var autoAddExpanded by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf<BillType?>(null) }
    var newCategoryName by remember { mutableStateOf("") }

    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var editCategoryName by remember { mutableStateOf("") }
    var editingMenuCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportFileName by remember { mutableStateOf("") }

    val autoAddDelaySeconds by viewModel.autoAddDelaySeconds.collectAsState()

    val notifListenerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    fun openNotificationListenerSettings() {
        val intent = android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS.let {
            android.content.Intent(it)
        }
        notifListenerLauncher.launch(intent)
    }

    fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.let {
                android.content.Intent(it, android.net.Uri.parse("package:${context.packageName}"))
            }
            notifListenerLauncher.launch(intent)
        }
    }

    LaunchedEffect(operationMessage) {
        operationMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.setAvatarUri(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        uri?.let { viewModel.exportToExcel(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportFileName = viewModel.getFileNameFromUri(it)
            pendingImportUri = it
        }
    }

    val isProcessing = processingState != BillViewModel.ProcessingState.IDLE
    val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.CHINESE).format(Date())

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .clickable { avatarLauncher.launch("image/*") }
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(Uri.parse(avatarUri))
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(text = "👤", fontSize = 36.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (avatarUri != null) "点击更换头像" else "点击设置头像",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Import / Export block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("数据管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text(
                        text = "共 ${allBills.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("导出为 Excel (.xlsx) 格式，支持导入备份",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { exportLauncher.launch("账单_${dateStamp}.xlsx") },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (processingState == BillViewModel.ProcessingState.EXPORTING) "导出中..." else "导出数据",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            ))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (processingState == BillViewModel.ProcessingState.IMPORTING) "导入中..." else "导入数据",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category Management
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoriesExpanded = !categoriesExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("分类管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (categoriesExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (categoriesExpanded) "收起" else "展开",
                        tint = Color.Gray
                    )
                }

                AnimatedVisibility(visible = categoriesExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("支出", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = ExpenseColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        expenseCats.forEach { cat ->
                            CategoryManageRow(
                                name = cat.name,
                                onLongPress = { editingMenuCategory = cat }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("收入", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = IncomeColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        incomeCats.forEach { cat ->
                            CategoryManageRow(
                                name = cat.name,
                                onLongPress = { editingMenuCategory = cat }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddCategoryDialog = BillType.EXPENSE; newCategoryName = "" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ExpenseColor),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseColor)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Text("支出类别", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { showAddCategoryDialog = BillType.INCOME; newCategoryName = "" },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, IncomeColor),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IncomeColor)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Text("收入类别", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 自动记账设置 =====
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoAddExpanded = !autoAddExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自动记账设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (autoAddExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (autoAddExpanded) "收起" else "展开",
                        tint = Color.Gray
                    )
                }

                AnimatedVisibility(visible = autoAddExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("监听付款通知后自动弹出添加账单窗口，并填写好金额",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Delay seconds setting
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("延迟时间：", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.setAutoAddDelaySeconds(autoAddDelaySeconds - 1) },
                                enabled = autoAddDelaySeconds > 1,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${autoAddDelaySeconds} 秒",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.setAutoAddDelaySeconds(autoAddDelaySeconds + 1) },
                                enabled = autoAddDelaySeconds < 30,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Notification listener permission
                        OutlinedButton(
                            onClick = { openNotificationListenerSettings() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("开启通知监听权限", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("需在系统设置中允许本应用读取通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)

                        Spacer(modifier = Modifier.height(8.dp))

                        // Notification permission detailed guide
                        Text("操作步骤：",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("1. 开启「通知监听权限」— 用于接收支付通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)
                        Text("2. 支持App：支付宝、微信、美团、抖音、京东、淘宝、拼多多",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Battery optimization
                        OutlinedButton(
                            onClick = { openBatteryOptimizationSettings() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("开启忽略电池优化", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("防止系统在后台杀死监听服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 自定义正则规则 =====
        val customPatterns by viewModel.customPatterns.collectAsState()
        var newPatternText by remember { mutableStateOf("") }
        var showPatternError by remember { mutableStateOf(false) }
        var patternsExpanded by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { patternsExpanded = !patternsExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自定义正则规则",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (patternsExpanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (patternsExpanded) "收起" else "展开",
                        tint = Color.Gray
                    )
                }

                AnimatedVisibility(visible = patternsExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("添加自定义匹配规则，用 * 表示金额位置",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("例如：您有一笔*人民币的消费",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newPatternText,
                                onValueChange = {
                                    newPatternText = it
                                    showPatternError = false
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("输入正则规则...") },
                                singleLine = true,
                                isError = showPatternError,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    if (newPatternText.isBlank() || !newPatternText.contains("*")) {
                                        showPatternError = true
                                    } else {
                                        viewModel.addCustomPattern(newPatternText.trim())
                                        newPatternText = ""
                                        showPatternError = false
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("添加")
                            }
                        }

                        if (showPatternError) {
                            Text("请输入有效规则（包含 * 号表示金额）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        if (customPatterns.isEmpty()) {
                            Text("暂无自定义规则",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(8.dp))
                        } else {
                            customPatterns.forEach { pattern ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pattern,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeCustomPattern(pattern) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Delete all data
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("危险操作", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                Spacer(modifier = Modifier.height(8.dp))
                Text("清空所有账单数据，此操作不可恢复",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteAllConfirm = true },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp))
                    Text("一键删除所有数据", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("记账本 v3.0",
            style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
    }

    // Import confirmation dialog
    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("确认导入", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("即将导入文件：")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pendingImportFileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = ExpenseColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("系统将自动识别第一个工作表（Sheet1）中的数据进行导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importFromExcel(pendingImportUri!!)
                        pendingImportUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseColor)
                ) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("取消") }
            }
        )
    }

    // Delete all confirmation
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除所有账单数据吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllBills()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("取消") }
            }
        )
    }

    // Delete category confirmation
    if (deletingCategory != null) {
        val cat = deletingCategory!!
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("删除类别", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除类别「${cat.name}」吗？该类别下的所有账单也将被删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategoryWithBills(cat)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("取消") }
            }
        )
    }

    // Add category dialog
    if (showAddCategoryDialog != null) {
        val type = showAddCategoryDialog!!
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = null },
            title = {
                Text("添加${if (type == BillType.EXPENSE) "支出" else "收入"}类别",
                    fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("类别名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName.trim(), type)
                            showAddCategoryDialog = null
                        }
                    },
                    enabled = newCategoryName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (type == BillType.EXPENSE) ExpenseColor else IncomeColor
                    )
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = null }) { Text("取消") }
            }
        )
    }

    // Edit category dialog
    if (editingCategory != null) {
        val cat = editingCategory!!
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("修改类别名称", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editCategoryName,
                    onValueChange = { editCategoryName = it },
                    label = { Text("类别名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editCategoryName.isNotBlank()) {
                            viewModel.updateCategoryWithBills(cat, editCategoryName.trim())
                            editingCategory = null
                        }
                    },
                    enabled = editCategoryName.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("取消") }
            }
        )
    }

    // Long-press action dialog for category
    if (editingMenuCategory != null) {
        val cat = editingMenuCategory!!
        AlertDialog(
            onDismissRequest = { editingMenuCategory = null },
            title = { Text(categoryDisplayName(cat.name), fontWeight = FontWeight.Bold) },
            text = { Text("请选择操作：") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = {
                            editCategoryName = cat.name
                            editingCategory = cat
                            editingMenuCategory = null
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Text("修改", modifier = Modifier.padding(start = 4.dp))
                    }
                    Button(
                        onClick = {
                            deletingCategory = cat
                            editingMenuCategory = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Text("删除", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMenuCategory = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryManageRow(
    name: String,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongPress
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = categoryDisplayName(name),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "长按操作",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}
