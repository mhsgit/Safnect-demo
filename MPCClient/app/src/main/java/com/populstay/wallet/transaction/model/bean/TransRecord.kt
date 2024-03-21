package com.populstay.wallet.transaction.model.bean

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.populstay.wallet.transaction.view.adapter.TransRecordListAdapter

@Entity(tableName = "transRecord")
data class TransRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /* var token_type: String? = null,
     var time: String? = null,
     var trans_type: String? = null,
     var trans_type_code: Int = TRANS_TYPE_CODE_TRANSFER,
     var trans_status: String? = null,
     var trans_status_code: Int = TRANS_STATUS_CODE_FAILED,
     var receiver_address: String? = null,
     var amount_val: String? = null,
     var fee_val: String? = null,
     var total_val: String? = null,
     var item_type :Int = TransRecordListAdapter.TYPE_ITEM,
     var sent_hash :String = "",
     var testNet :Boolean = false*/

    @ColumnInfo(name = "token_type") var token_type: String? = null,
    @ColumnInfo(name = "time") var time: String? = null,
    @ColumnInfo(name = "trans_type") var trans_type: String? = null,
    @ColumnInfo(name = "trans_type_code") var trans_type_code: Int = TRANS_TYPE_CODE_TRANSFER,
    @ColumnInfo(name = "trans_status") var trans_status: String? = null,
    @ColumnInfo(name = "trans_status_code") var trans_status_code: Int = TRANS_STATUS_CODE_FAILED,
    @ColumnInfo(name = "receiver_address") var receiver_address: String? = null,
    @ColumnInfo(name = "amount_val") var amount_val: String? = null,
    @ColumnInfo(name = "fee_val") var fee_val: String? = null,
    @ColumnInfo(name = "total_val") var total_val: String? = null,
    @ColumnInfo(name = "item_type") var item_type :Int = TransRecordListAdapter.TYPE_ITEM,
    @ColumnInfo(name = "sent_hash") var sent_hash :String = "",
    @ColumnInfo(name = "testNet") var testNet :Boolean = false
){
    companion object{
        const val TRANS_STATUS_CODE_SUCCESS = 1
        const val TRANS_STATUS_CODE_FAILED = 0
        const val TRANS_STATUS_CODE_RESERVE = 2
        const val TRANS_STATUS_CODE_PENDING = 3

        const val TRANS_TYPE_CODE_TRANSFER = 0
        const val TRANS_TYPE_CODE_RECEIVE = 1

        const val TRANS_STATUS_CODE_SUCCESS_TXT = "Success"
        const val TRANS_STATUS_CODE_FAILED_TXT = "Failed"
        const val TRANS_STATUS_CODE_RESERVE_TXT = "Reserve"
        const val TRANS_STATUS_CODE_PENDING_TXT = "Pending"
    }

}


