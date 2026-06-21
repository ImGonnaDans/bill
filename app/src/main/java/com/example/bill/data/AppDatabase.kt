package com.example.bill.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Bill::class, CategoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val defaultExpenseNames = listOf(
            "餐饮", "交通", "购物", "娱乐", "居住", "服饰", "医疗", "教育", "其他"
        )
        private val defaultIncomeNames = listOf(
            "薪资", "理财", "奖金", "兼职", "红包", "经营", "其他"
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bill_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(SeedCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                for (name in defaultExpenseNames) {
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type) VALUES ('$name', 'EXPENSE')")
                }
                for (name in defaultIncomeNames) {
                    db.execSQL("INSERT OR IGNORE INTO categories (name, type) VALUES ('$name', 'INCOME')")
                }
            }
        }
    }
}
