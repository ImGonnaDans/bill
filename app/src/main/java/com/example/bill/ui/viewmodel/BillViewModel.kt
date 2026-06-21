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
import com.example.bill.data.CategoryDao
import com.example.bill.data.CategoryEntity
import com.example.bill.data.CategoryTotal
import com.example.bill.data.DailyTotal
import com.example.bill.data.ExcelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BillRepository
    private val categoryDao: CategoryDao

    private val prefs = application.getSharedPreferences("bill_prefs", Context.MODE_PRIVATE)

    private val _avatarUri = MutableStateFlow<String?>(prefs.getString("avatar_uri", null))
    val avatarUri: StateFlow<String?> = _avatarUri

    fun setAvatarUri(uri: String?) {
        _avatarUri.value = uri
        prefs.edit().putString("avatar_uri", uri).apply()
    }

    val allBills: StateFlow<List<Bill>>

    private val _selectedDateMillis = MutableStateFlow(System.currentTimeMillis())
    val selectedDateMillis: StateFlow<Long> = _selectedDateMillis

    val billsForSelectedDate: StateFlow<List<Bill>>

    private val _statsStartMillis = MutableStateFlow(startOfCurrentMonth())
    val statsStartMillis: StateFlow<Long> = _statsStartMillis

    private val _statsEndMillis = MutableStateFlow(endOfCurrentMonth())
    val statsEndMillis: StateFlow<Long> = _statsEndMillis

    private val _selectedStatsType = MutableStateFlow(BillType.EXPENSE)
    val selectedStatsType: StateFlow<BillType> = _selectedStatsType

    private val _statsYear = MutableStateFlow(getCurrentYear())
    val statsYear: StateFlow<Int> = _statsYear

    private val _statsMonth = MutableStateFlow(getCurrentMonth())
    val statsMonth: StateFlow<Int> = _statsMonth

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BillRepository(database.billDao())
        categoryDao = database.categoryDao()

        allBills = repository.getAllBills()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        billsForSelectedDate = combine(
            repository.getAllBills(),
            _selectedDateMillis
        ) { bills, dateMillis ->
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)
            bills.filter { bill ->
                val bc = Calendar.getInstance().apply { timeInMillis = bill.dateMillis }
                bc.get(Calendar.YEAR) == year &&
                        bc.get(Calendar.MONTH) == month &&
                        bc.get(Calendar.DAY_OF_MONTH) == day
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            repository.delete(bill)
        }
    }

    // ===== Category Management =====

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
        }
    }

    fun deleteCategoryWithBills(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.deleteBillsByCategory(category.name, category.type)
            categoryDao.delete(category)
            _operationMessage.value = "已删除 $count 条账单"
        }
    }

    // ===== Delete All Bills =====

    fun deleteAllBills() {
        viewModelScope.launch(Dispatchers.IO) {
            categoryDao.deleteAllBills()
            _operationMessage.value = "已删除所有账单数据"
        }
    }

    // ===== Stats Queries =====

    fun setSelectedDate(dateMillis: Long) {
        _selectedDateMillis.value = dateMillis
    }

    fun setStatsPeriod(year: Int, month: Int) {
        _statsYear.value = year
        _statsMonth.value = month
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

    fun setStatsCustomPeriod(startMillis: Long, endMillis: Long) {
        _statsStartMillis.value = startMillis
        _statsEndMillis.value = endMillis
    }

    fun setSelectedStatsType(type: BillType) {
        _selectedStatsType.value = type
    }

    fun getBillsBetween(startMillis: Long, endMillis: Long): Flow<List<Bill>> =
        repository.getBillsBetween(startMillis, endMillis)

    fun getBillsBetweenByType(startMillis: Long, endMillis: Long, type: BillType): Flow<List<Bill>> =
        repository.getBillsBetweenByType(startMillis, endMillis, type)

    fun getTotalByTypeBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<Long?> =
        repository.getTotalByTypeBetween(startMillis, endMillis, type)

    fun getCategoryTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<CategoryTotal>> =
        repository.getCategoryTotalsBetween(startMillis, endMillis, type)

    fun getDailyTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<DailyTotal>> =
        repository.getDailyTotalsBetween(startMillis, endMillis, type)

    fun getTotalExpenseBetween(startMillis: Long, endMillis: Long): Flow<Long?> =
        repository.getTotalByTypeBetween(startMillis, endMillis, BillType.EXPENSE)

    fun getTotalIncomeBetween(startMillis: Long, endMillis: Long): Flow<Long?> =
        repository.getTotalByTypeBetween(startMillis, endMillis, BillType.INCOME)

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

                // Read ALL bytes first, then parse — avoids ContentResolver stream issues
                val fileBytes = withContext(Dispatchers.IO) {
                    inputStream.use { it.readBytes() }
                }
                Log.d("IMPORT", "Read ${fileBytes.size} bytes from URI")
                if (fileBytes.size >= 4) {
                    val magic = fileBytes.take(4).joinToString("") { "%02x".format(it) }
                    Log.d("IMPORT", "First 4 bytes (ZIP magic): $magic")
                    if (magic != "504b0304") {
                        Log.e("IMPORT", "NOT a valid ZIP file! Expected PK\\u0003\\u0004 but got $magic")
                    }
                } else {
                    Log.e("IMPORT", "File too small: ${fileBytes.size} bytes")
                }

                val result = withContext(Dispatchers.IO) {
                    ExcelManager().importFromExcelBytes(fileBytes)
                }

                Log.d("IMPORT", "result: rows=${result.rows.size}, errors=${result.errors.size}")
                if (result.rows.isNotEmpty()) {
                    Log.d("IMPORT", "first row sample: type=${result.rows[0].type}, cat=${result.rows[0].category}, amount=${result.rows[0].amountInCents}")
                } else {
                    Log.w("IMPORT", "No rows parsed. Checking errors...")
                    result.errors.forEach { Log.w("IMPORT", "  error: $it") }
                }

                var importedCount = 0
                if (result.rows.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        for (row in result.rows) {
                            // Auto-create category if it does not exist
                            ensureCategoryExists(row.category, row.type)
                            // Insert bill
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
        fun startOfCurrentMonth(): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        fun endOfCurrentMonth(): Long {
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
