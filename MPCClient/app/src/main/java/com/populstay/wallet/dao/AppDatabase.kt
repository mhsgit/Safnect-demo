package com.populstay.wallet.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.populstay.wallet.transaction.model.bean.TransRecord

@Database(entities = [TransRecord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transRecordDao(): TransRecordDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "app-db"
                ).build().also { instance = it }
            }
        }
    }
}
