package com.example.bill.data

import kotlinx.coroutines.flow.Flow

class BillRepository(private val billDao: BillDao) {

    suspend fun insert(bill: Bill) = billDao.insert(bill)

    suspend fun update(bill: Bill) = billDao.update(bill)

    suspend fun delete(bill: Bill) = billDao.delete(bill)

    fun getAllBills(): Flow<List<Bill>> = billDao.getAllBills()

    fun getBillsBetween(startMillis: Long, endMillis: Long): Flow<List<Bill>> =
        billDao.getBillsBetween(startMillis, endMillis)

    fun getBillsBetweenByType(startMillis: Long, endMillis: Long, type: BillType): Flow<List<Bill>> =
        billDao.getBillsBetweenByType(startMillis, endMillis, type)

    suspend fun getAllBillsList(): List<Bill> = billDao.getAllBillsList()

    suspend fun getLatestBill(): Bill? = billDao.getLatestBill()

    fun getTotalByTypeBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<Long?> =
        billDao.getTotalByTypeBetween(startMillis, endMillis, type)

    fun getCategoryTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<CategoryTotal>> =
        billDao.getCategoryTotalsBetween(startMillis, endMillis, type)

    fun getDailyTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<DailyTotal>> =
        billDao.getDailyTotalsBetween(startMillis, endMillis, type)

    suspend fun updateCategoryNameInBills(oldName: String, newName: String, type: BillType): Int =
        billDao.updateCategoryNameInBills(oldName, newName, type)

    suspend fun deleteBillsByCategory(category: String, type: BillType): Int =
        billDao.deleteBillsByCategory(category, type)

    suspend fun getTotalByTypeBetweenSuspend(startMillis: Long, endMillis: Long, type: BillType): Long? =
        billDao.getTotalByTypeBetweenSuspend(startMillis, endMillis, type)

    suspend fun getCountByTypeBetween(startMillis: Long, endMillis: Long, type: BillType): Int =
        billDao.getCountByTypeBetween(startMillis, endMillis, type)

    suspend fun getCountBetween(startMillis: Long, endMillis: Long): Int =
        billDao.getCountBetween(startMillis, endMillis)

    suspend fun getCategoryBreakdownBetween(startMillis: Long, endMillis: Long, type: BillType): List<CategoryBreakdown> =
        billDao.getCategoryBreakdownBetween(startMillis, endMillis, type)
}
