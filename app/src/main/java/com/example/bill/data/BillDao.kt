package com.example.bill.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: Bill)

    @Update
    suspend fun update(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)

    @Query("SELECT * FROM bills ORDER BY dateMillis DESC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE dateMillis BETWEEN :startMillis AND :endMillis ORDER BY dateMillis DESC")
    fun getBillsBetween(startMillis: Long, endMillis: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE dateMillis BETWEEN :startMillis AND :endMillis AND type = :type ORDER BY dateMillis DESC")
    fun getBillsBetweenByType(startMillis: Long, endMillis: Long, type: BillType): Flow<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY dateMillis ASC")
    suspend fun getAllBillsList(): List<Bill>

    @Query("SELECT * FROM bills ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getLatestBill(): Bill?

    @Query("SELECT SUM(amountInCents) FROM bills WHERE dateMillis BETWEEN :startMillis AND :endMillis AND type = :type")
    fun getTotalByTypeBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<Long?>

    @Query("SELECT category, SUM(amountInCents) as total FROM bills WHERE dateMillis BETWEEN :startMillis AND :endMillis AND type = :type GROUP BY category")
    fun getCategoryTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<CategoryTotal>>

    @Query("SELECT (dateMillis / 86400000) as dayStamp, SUM(amountInCents) as total FROM bills WHERE dateMillis BETWEEN :startMillis AND :endMillis AND type = :type GROUP BY dayStamp ORDER BY dayStamp ASC")
    fun getDailyTotalsBetween(startMillis: Long, endMillis: Long, type: BillType): Flow<List<DailyTotal>>

    @Query("UPDATE bills SET category = :newName WHERE category = :oldName AND type = :type")
    suspend fun updateCategoryNameInBills(oldName: String, newName: String, type: BillType): Int

    @Query("DELETE FROM bills WHERE category = :category AND type = :type")
    suspend fun deleteBillsByCategory(category: String, type: BillType): Int
}

data class CategoryTotal(
    val category: String,
    val total: Long
)

data class DailyTotal(
    val dayStamp: Long,
    val total: Long
)
