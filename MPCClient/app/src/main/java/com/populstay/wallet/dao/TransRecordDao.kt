package com.populstay.wallet.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.populstay.wallet.transaction.model.bean.TransRecord

@Dao
interface TransRecordDao {
    @Insert
    suspend fun insert(user: TransRecord)

    // 查询所有用户
    @Query("SELECT * FROM transRecord")
    suspend fun getAllTransRecords(): List<TransRecord>

    @Query("SELECT * FROM transRecord WHERE sent_hash = :sent_hash")
    suspend fun getTransRecordBySentHash(sent_hash: String): TransRecord

    // 根据ID查询用户是否存在
    @Query("SELECT COUNT(*) FROM transRecord WHERE sent_hash = :sent_hash")
    suspend fun checkTransRecordExists(sent_hash: String): Int

    // 删除单个用户
    @Delete
    suspend fun deleteTransRecord(user: TransRecord)

    // 根据ID删除用户
    @Query("DELETE FROM transRecord WHERE sent_hash = :sent_hash")
    suspend fun deleteTransRecordBySentHash(sent_hash: String)

    // 删除所有用户
    @Query("DELETE FROM transRecord")
    suspend fun deleteAllTransRecords()

}