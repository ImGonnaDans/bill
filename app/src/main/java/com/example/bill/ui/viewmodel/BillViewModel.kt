package com.example.bill.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bill.data.AppDatabase
import com.example.bill.data.Bill
import com.example.bill.data.BillRepository
import com.example.bill.data.BillType
import com.example.bill.data.CategoryBreakdown
import com.example.bill.data.CategoryDao
import com.example.bill.data.CategoryEntity
import com.example.bill.data.CategoryTotal
import com.example.bill.data.DailyTotal
import com.example.bill.data.ExcelManager
import com.example.bill.service.NotificationMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import androidx.core.content.edit

enum class StatsMode {
    YEAR, MONTH, CUSTOM
}

data class MonthSummary(
    val totalExpense: Long = 0,
    val totalIncome: Long = 0
)

class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BillRepository
    private val categoryDao: CategoryDao

    private val prefs = application.getSharedPreferences("bill_prefs", Context.MODE_PRIVATE)
    private val autoAddPrefs = application.getSharedPreferences("auto_add_prefs", Context.MODE_PRIVATE)

    private val _avatarUri = MutableStateFlow(prefs.getString("avatar_uri", null))
    val avatarUri: StateFlow<String?> = _avatarUri

    private val _autoAddDelaySeconds = MutableStateFlow(
        autoAddPrefs.getInt("auto_add_delay_seconds", 5)
    )
    val autoAddDelaySeconds: StateFlow<Int> = _autoAddDelaySeconds

    private val _customPatterns = MutableStateFlow(
        NotificationMonitorService.getCustomPatterns(application)
    )
    val customPatterns: StateFlow<List<String>> = _customPatterns

    fun setAutoAddDelaySeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 30)
        _autoAddDelaySeconds.value = clamped
        autoAddPrefs.edit { putInt("auto_add_delay_seconds", clamped) }
    }

    fun addCustomPattern(pattern: String) {
        val current = _customPatterns.value.toMutableList()
        if (pattern.isNotBlank() && pattern !in current) {
            current.add(pattern.trim())
            _customPatterns.value = current
            NotificationMonitorService.setCustomPatterns(getApplication(), current)
        }
    }

    fun removeCustomPattern(pattern: String) {
        val current = _customPatterns.value.toMutableList()
        current.remove(pattern)
        _customPatterns.value = current
        NotificationMonitorService.setCustomPatterns(getApplication(), current)
    }

    fun setAvatarUri(uri: Uri?) {
        if (uri == null) {
            // Clear avatar
            _avatarUri.value = null
            prefs.edit { remove("avatar_uri") }
            File(getApplication<Application>().filesDir, "avatar.jpg").delete()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val outputFile = File(context.filesDir, "avatar.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    _operationMessage.value = "无法读取所选图片"
                    return@launch
                }
                val fileUri = Uri.fromFile(outputFile).toString()
                _avatarUri.value = fileUri
                prefs.edit { putString("avatar_uri", fileUri) }
            } catch (e: Exception) {
                _operationMessage.value = "保存头像失败: ${e.message}"
            }
        }
    }

    val allBills: StateFlow<List<Bill>>

    // ===== Bill Page: Month Summary =====
    private val _monthSummary = MutableStateFlow(MonthSummary())
    val monthSummary: StateFlow<MonthSummary> = _monthSummary

    private val _currentMonthStart = MutableStateFlow(startOfCurrentMonthMillis())
    private val _currentMonthEnd = MutableStateFlow(endOfCurrentMonthMillis())

    fun refreshMonthSummary() {
        viewModelScope.launch(Dispatchers.IO) {
            val start = _currentMonthStart.value
            val end = _currentMonthEnd.value
            val expense = repository.getTotalByTypeBetweenSuspend(start, end, BillType.EXPENSE) ?: 0L
            val income = repository.getTotalByTypeBetweenSuspend(start, end, BillType.INCOME) ?: 0L
            _monthSummary.value = MonthSummary(totalExpense = expense, totalIncome = income)
        }
    }

    // ===== Stats Page: Mode & Period =====
    private val _statsMode = MutableStateFlow(StatsMode.MONTH)
    val statsMode: StateFlow<StatsMode> = _statsMode

    fun setStatsMode(mode: StatsMode) {
        _statsMode.value = mode
        recalcStatsPeriod()
    }

    // Year navigation
    private val _statsYear = MutableStateFlow(getCurrentYear())
    val statsYear: StateFlow<Int> = _statsYear

    // Month navigation (1-based)
    private val _statsMonth = MutableStateFlow(getCurrentMonth())
    val statsMonth: StateFlow<Int> = _statsMonth

    // Custom range
    private val _customStartMillis = MutableStateFlow(startOfCurrentMonthMillis())

    private val _customEndMillis = MutableStateFlow(endOfCurrentMonthMillis())

    // Computed stats period
    private val _statsStartMillis = MutableStateFlow(0L)
    val statsStartMillis: StateFlow<Long> = _statsStartMillis

    private val _statsEndMillis = MutableStateFlow(0L)
    val statsEndMillis: StateFlow<Long> = _statsEndMillis

    // Stats type selection
    private val _selectedStatsType = MutableStateFlow(BillType.EXPENSE)
    val selectedStatsType: StateFlow<BillType> = _selectedStatsType

    // Stats data (loaded on demand)
    private val _statsExpenseTotal = MutableStateFlow(0L)
    val statsExpenseTotal: StateFlow<Long> = _statsExpenseTotal

    private val _statsIncomeTotal = MutableStateFlow(0L)
    val statsIncomeTotal: StateFlow<Long> = _statsIncomeTotal

    private val _statsCount = MutableStateFlow(0)
    val statsCount: StateFlow<Int> = _statsCount

    private val _statsDailyAvg = MutableStateFlow(0.0)
    val statsDailyAvg: StateFlow<Double> = _statsDailyAvg

    private val _statsCategoryBreakdown = MutableStateFlow<List<CategoryBreakdown>>(emptyList())
    val statsCategoryBreakdown: StateFlow<List<CategoryBreakdown>> = _statsCategoryBreakdown

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BillRepository(database.billDao())
        categoryDao = database.categoryDao()

        allBills = repository.getAllBills()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Initialize period
        recalcStatsPeriod()
        refreshMonthSummary()
    }

    private fun recalcStatsPeriod() {
        val mode = _statsMode.value
        val year = _statsYear.value
        val month = _statsMonth.value

        when (mode) {
            StatsMode.YEAR -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, 0)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                _statsStartMillis.value = cal.timeInMillis
                cal.set(Calendar.MONTH, 11)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                _statsEndMillis.value = cal.timeInMillis
            }
            StatsMode.MONTH -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                _statsStartMillis.value = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                _statsEndMillis.value = cal.timeInMillis
            }
            StatsMode.CUSTOM -> {
                // Use custom values directly
            }
        }
        loadStatsData()
    }

    fun loadStatsData() {
        viewModelScope.launch(Dispatchers.IO) {
            val start = _statsStartMillis.value
            val end = _statsEndMillis.value
            val type = _selectedStatsType.value

            val expense = repository.getTotalByTypeBetweenSuspend(start, end, BillType.EXPENSE) ?: 0L
            val income = repository.getTotalByTypeBetweenSuspend(start, end, BillType.INCOME) ?: 0L
            val count = repository.getCountByTypeBetween(start, end, type)
            val breakdown = repository.getCategoryBreakdownBetween(start, end, type)

            // Calculate daily average (number of days in period)
            val days = ((end - start) / 86400000 + 1).coerceAtLeast(1)

            _statsExpenseTotal.value = expense
            _statsIncomeTotal.value = income
            _statsCount.value = count
            _statsDailyAvg.value = if (count > 0) count.toDouble() / days else 0.0
            _statsCategoryBreakdown.value = breakdown
        }
    }

    // ===== Year Navigation =====
    fun increaseYear() {
        _statsYear.value += 1
        recalcStatsPeriod()
    }

    fun decreaseYear() {
        _statsYear.value -= 1
        recalcStatsPeriod()
    }

    // ===== Month Navigation =====
    fun increaseMonth() {
        var y = _statsYear.value
        var m = _statsMonth.value + 1
        if (m > 12) { m = 1; y++ }
        _statsYear.value = y
        _statsMonth.value = m
        recalcStatsPeriod()
    }

    fun decreaseMonth() {
        var y = _statsYear.value
        var m = _statsMonth.value - 1
        if (m < 1) { m = 12; y-- }
        _statsYear.value = y
        _statsMonth.value = m
        recalcStatsPeriod()
    }

    // ===== Custom Range =====
    fun setCustomRange(startMillis: Long, endMillis: Long) {
        _customStartMillis.value = startMillis
        _customEndMillis.value = endMillis
        _statsStartMillis.value = startMillis
        _statsEndMillis.value = endMillis
        loadStatsData()
    }

    // ===== Stats Type =====
    fun setSelectedStatsType(type: BillType) {
        _selectedStatsType.value = type
        loadStatsData()
    }

    // ===== Bill CRUD =====

    fun addBill(type: BillType, category: String, amountInCents: Long, dateMillis: Long, note: String = "") {
        viewModelScope.launch {
            repository.insert(
                Bill(
                    type = type,
                    category = category,
                    amountInCents = amountInCents,
                    dateMillis = dateMillis,
                    note = note
                )
            )
            refreshMonthSummary()
            loadStatsData()
        }
    }

    fun updateBill(id: Long, type: BillType, category: String, amountInCents: Long, dateMillis: Long, note: String) {
        viewModelScope.launch {
            repository.update(
                Bill(
                    id = id,
                    type = type,
                    category = category,
                    amountInCents = amountInCents,
                    dateMillis = dateMillis,
                    note = note
                )
            )
            refreshMonthSummary()
            loadStatsData()
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            repository.delete(bill)
            refreshMonthSummary()
            loadStatsData()
        }
    }

    // ===== Category Management =====

    fun getDailyTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<DailyTotal>> =
        repository.getDailyTotalsBetween(startMillis, endMillis, type)

    fun getCategoryTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<CategoryTotal>> =
        repository.getCategoryTotalsBetween(startMillis, endMillis, type)

    fun getCategoriesByType(type: BillType): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByType(type)

    suspend fun ensureCategoryExists(name: String, type: BillType) {
        withContext(Dispatchers.IO) {
            val existing = categoryDao.findByNameAndType(name, type)
            if (existing == null) {
                categoryDao.insert(CategoryEntity(name = name, type = type))
            }
        }
    }

    fun addCategory(name: String, type: BillType) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = categoryDao.findByNameAndType(name, type)
            if (existing == null) {
                categoryDao.insert(CategoryEntity(name = name, type = type))
            }
        }
    }

    fun updateCategoryWithBills(oldCategory: CategoryEntity, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.updateCategoryNameInBills(oldCategory.name, newName, oldCategory.type)
            categoryDao.update(oldCategory.copy(name = newName))
            _operationMessage.value = "已修改 $count 条账单的类别为「$newName」"
            refreshMonthSummary()
            loadStatsData()
        }
    }

    fun deleteCategoryWithBills(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.deleteBillsByCategory(category.name, category.type)
            categoryDao.delete(category)
            _operationMessage.value = "已删除 $count 条账单"
            refreshMonthSummary()
            loadStatsData()
        }
    }

    // ===== Delete All Bills =====

    fun deleteAllBills() {
        viewModelScope.launch(Dispatchers.IO) {
            categoryDao.deleteAllBills()
            _operationMessage.value = "已删除所有账单数据"
            refreshMonthSummary()
            loadStatsData()
        }
    }

    // ===== Excel Import / Export =====

    private val _processingState = MutableStateFlow(ProcessingState.IDLE)
    val processingState: StateFlow<ProcessingState> = _processingState

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage

    fun consumeMessage() {
        _operationMessage.value = null
    }

    fun exportToExcel(uri: Uri) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.EXPORTING
            try {
                val bills = withContext(Dispatchers.IO) { repository.getAllBillsList() }
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                val outputStream = contentResolver.openOutputStream(uri)

                if (outputStream == null) {
                    _operationMessage.value = "无法打开文件进行写入"
                    _processingState.value = ProcessingState.IDLE
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    outputStream.use { stream ->
                        ExcelManager().exportToExcel(bills, stream)
                    }
                }

                _operationMessage.value = "导出成功！共导出 ${bills.size} 条账单"
            } catch (e: Exception) {
                _operationMessage.value = "导出失败: ${e.message}"
            } finally {
                _processingState.value = ProcessingState.IDLE
            }
        }
    }

    fun importFromExcel(uri: Uri) {
        viewModelScope.launch {
            _processingState.value = ProcessingState.IMPORTING
            try {
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _operationMessage.value = "无法打开文件进行读取"
                    _processingState.value = ProcessingState.IDLE
                    return@launch
                }

                val fileBytes = withContext(Dispatchers.IO) {
                    inputStream.use { it.readBytes() }
                }
                Log.d("IMPORT", "Read ${fileBytes.size} bytes from URI")
                if (fileBytes.size >= 4) {
                    val magic = fileBytes.take(4).joinToString("") { "%02x".format(it) }
                    Log.d("IMPORT", "First 4 bytes (ZIP magic): $magic")
                }

                val result = withContext(Dispatchers.IO) {
                    ExcelManager().importFromExcelBytes(fileBytes)
                }

                var importedCount = 0
                if (result.rows.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        for (row in result.rows) {
                            ensureCategoryExists(row.category, row.type)
                            repository.insert(
                                Bill(
                                    type = row.type,
                                    category = row.category,
                                    amountInCents = row.amountInCents,
                                    dateMillis = row.dateMillis,
                                    note = row.note
                                )
                            )
                            importedCount++
                        }
                    }
                }

                val msg = buildString {
                    append("导入完成！共导入 $importedCount 条账单")
                    if (result.errors.isNotEmpty()) {
                        append("\n${result.errors.size} 条解析失败:")
                        result.errors.take(5).forEach { append("\n  $it") }
                        if (result.errors.size > 5) {
                            append("\n  ...还有 ${result.errors.size - 5} 个错误")
                        }
                    }
                }
                _operationMessage.value = msg
                refreshMonthSummary()
                loadStatsData()
            } catch (e: Exception) {
                _operationMessage.value = "导入失败: ${e.message}"
            } finally {
                _processingState.value = ProcessingState.IDLE
            }
        }
    }

    fun getFileNameFromUri(uri: Uri): String {
        return try {
            val context = getApplication<Application>()
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                if (nameIndex >= 0) it.getString(nameIndex) else "未知文件"
            } ?: uri.lastPathSegment ?: "未知文件"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "未知文件"
        }
    }

    enum class ProcessingState {
        IDLE, EXPORTING, IMPORTING
    }

    companion object {
        fun startOfCurrentMonthMillis(): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun endOfCurrentMonthMillis(): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return cal.timeInMillis
        }

        fun getCurrentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

        fun getCurrentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1
    }
}