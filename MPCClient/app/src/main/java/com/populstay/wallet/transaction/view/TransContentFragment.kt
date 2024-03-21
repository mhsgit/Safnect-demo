package com.populstay.wallet.transaction.view

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.populstay.wallet.BaseApp
import com.populstay.wallet.CommonUtil
import com.populstay.wallet.FileUitl
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.R
import com.populstay.wallet.dao.AppDatabase
import com.populstay.wallet.databinding.FragmentTransContentBinding
import com.populstay.wallet.eventbus.Event
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.mpc.IMpc
import com.populstay.wallet.mpc.ImplMpc
import com.populstay.wallet.proto.WalletMessage
import com.populstay.wallet.transaction.model.bean.TransRecord
import com.populstay.wallet.transaction.view.adapter.TransRecordListAdapter
import com.populstay.wallet.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class TransContentFragment : BaseFragment<FragmentTransContentBinding>() {

    companion object {
        private const val ARG_PARAM_TRANS_TYPE = "trans_type"
        const val TRANS_TYPE_ALL = 0
        const val TRANS_TYPE_TRANSFER = 1
        const val TRANS_TYPE_RECEIVE = 2

        fun newInstance() = TransContentFragment()
        @JvmStatic
        fun newInstance(transType: Int) =
            TransContentFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PARAM_TRANS_TYPE, transType)
                }
            }
    }
    private var transType: Int = TRANS_TYPE_ALL

    private var transRecordList: MutableList<TransRecord> = ArrayList()
    private lateinit var transRecordListAdapter: TransRecordListAdapter

    private var isFirstLoad = true

    private val mpc by lazy {
        ImplMpc()
    }
    private fun showLoadView(){
        if(null != binding){
            binding.refreshLayout.visibility = View.GONE
            binding.avLoadingIndicatorView.visibility = View.VISIBLE
        }
    }

    private fun stopLoadView(){
        if(null != binding){
            binding.refreshLayout.visibility = View.VISIBLE
            binding.avLoadingIndicatorView.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isFirstLoad = true
        arguments?.let {
            transType = it.getInt(ARG_PARAM_TRANS_TYPE,TRANS_TYPE_ALL)
        }
    }

    override val bindingInflater: (LayoutInflater, ViewGroup?, Bundle?) -> FragmentTransContentBinding
        get() = { layoutInflater, viewGroup, _ ->
            FragmentTransContentBinding.inflate(layoutInflater, viewGroup, false)
    }

    override fun initView() {
        super.initView()
        initData()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Event?) {
        if (null == event || event.type <= 0) {
            PeachLogger.d("onEvent--无效事件")
            return
        }

        when(event.type){
            Event.EventType.BACK_TRANSFER ->{
                reshuseData()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        // 这里关闭会闪退，不知道为什么
        //stopLoadView()
    }

    private fun initData() {
        /*when (transType) {
            TRANS_TYPE_TRANSFER -> {
                transRecordList.add(TransRecord(
                    token_type = "ETH",time = "2023-08 18 20:07:26", trans_type = "Transfer",
                    trans_status = "Success",trans_status_code = TransRecord.TRANS_STATUS_CODE_SUCCESS, receiver_address = "ghyg7687987fadh6788768888977898dbzhb89",
                    amount_val = "0.001124 ETH",fee_val = "0.0000100 ETH", total_val = "0.0001224 ETH",
                    trans_type_code = TransRecord.TRANS_TYPE_CODE_TRANSFER
                ))

            }
            TRANS_TYPE_RECEIVE -> {
                transRecordList.add(TransRecord(
                    token_type = "ETH",time = "2022-08 18 20:07:26", trans_type = "Receive",
                    trans_status = "Failed",trans_status_code = TransRecord.TRANS_STATUS_CODE_FAILED, receiver_address = "ghyg7687987fadh6788768888977898dbzhb89",
                    amount_val = "0.0000100 ETH",fee_val = "0.000050 ETH", total_val = "0.0000150 ETH",
                    trans_type_code = TransRecord.TRANS_TYPE_CODE_RECEIVE
                ))
            }
            else -> {
                transRecordList.add(TransRecord(
                    token_type = "ETH",time = "2023-08 18 20:07:26", trans_type = "Transfer",
                    trans_status = "Success",trans_status_code = TransRecord.TRANS_STATUS_CODE_SUCCESS, receiver_address = "ghyg7687987fadh6788768888977898dbzhb89",
                    amount_val = "0.001124 ETH",fee_val = "0.0000100 ETH", total_val = "0.0001224 ETH",
                    trans_type_code = TransRecord.TRANS_TYPE_CODE_TRANSFER
                ))

                transRecordList.add(TransRecord(
                    token_type = "ETH",time = "2022-08 18 20:07:26", trans_type = "Receive",
                    trans_status = "Failed",trans_status_code = TransRecord.TRANS_STATUS_CODE_FAILED, receiver_address = "ghyg7687987fadh6788768888977898dbzhb89",
                    amount_val = "0.0000100 ETH",fee_val = "0.000050 ETH", total_val = "0.0000150 ETH",
                    trans_type_code = TransRecord.TRANS_TYPE_CODE_RECEIVE
                ))
            }
        }*/

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }

        transRecordListAdapter = TransRecordListAdapter(requireContext(),transRecordList)
        binding.transRecordListView.layoutManager = LinearLayoutManager(context)
        binding.transRecordListView.adapter = transRecordListAdapter

        binding.refreshLayout.setColorSchemeColors(resources.getColor(R.color.color_ff0b0bd8))
        binding.refreshLayout.setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener {
            binding.refreshLayout.post {
                binding.refreshLayout.isRefreshing = true
                reshuseData(true)
            }
        })
    }

    private var isReshuseDataing = false
    private var reshuseDataJob: Job? = null
    @SuppressLint("NotifyDataSetChanged")
    private fun reshuseData(isDropDown : Boolean = false) {
        if (isReshuseDataing){
            Log.d(GlobalConstant.APP_TAG, "isReshuseDataing = $isReshuseDataing")
            return
        }

        if (!isDropDown){
            showLoadView()
        }

        reshuseDataJob?.cancel()
        reshuseDataJob = lifecycleScope.launch {
            var tempList: MutableList<TransRecord> = ArrayList()
            isReshuseDataing = true
            withContext(Dispatchers.IO) {
                val sender = mpc.getMyAddress(IMpc.ROLE_CLIENT, FileUitl.getConfigDir())
                var info : WalletMessage.GetTransactionResult? = null
                // 测试网
                sender?.let {
                    info = mpc.getTransactionList(sender,true)
                }
                dealRecord(true,info, sender,tempList)

                // 主网
                sender?.let {
                    info = mpc.getTransactionList(sender,false)
                }
                dealRecord(false,info, sender,tempList)

                // 需要查询本地转账记录
                if (transType != TRANS_TYPE_RECEIVE){
                    // 查询本地记录
                    var localList: List<TransRecord> =  AppDatabase.getInstance(BaseApp.mContext).transRecordDao().getAllTransRecords()
                    if (localList.isNotEmpty()){
                        var tempLocalList: MutableList<TransRecord> = ArrayList()
                        var tempRemoteList: MutableList<TransRecord> = ArrayList()
                        localList.forEach { localTransRecord ->
                            var isExist = false
                            tempList.forEach { remoteTransRecord ->
                                if (localTransRecord.sent_hash.isEmpty() || localTransRecord.sent_hash.trim().isEmpty()){
                                    // 无效的数据
                                    AppDatabase.getInstance(BaseApp.mContext).transRecordDao().deleteTransRecord(localTransRecord)
                                }else{
                                    // 交易状态已经完成
                                    if (localTransRecord.sent_hash == remoteTransRecord.sent_hash){
                                        AppDatabase.getInstance(BaseApp.mContext).transRecordDao().deleteTransRecord(localTransRecord)
                                        tempRemoteList.add(remoteTransRecord)
                                    }else{
                                        // pending
                                        isExist = true
                                    }
                                }

                            }
                            if (isExist){
                                tempLocalList.add(localTransRecord)
                            }
                        }

                        if (tempRemoteList.isNotEmpty()){
                            tempList.removeAll(tempRemoteList)
                        }

                        if (tempLocalList.isNotEmpty()){
                            tempList.addAll(tempLocalList)
                            val newTempList = tempList.sortedByDescending { it.time }
                            tempList.clear()
                            tempList.addAll(newTempList)
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                stopLoadView()
                binding.refreshLayout.isRefreshing = false
                transRecordList.clear()
                if (tempList.isEmpty()){
                    val noDataItem = TransRecord()
                    noDataItem.item_type = TransRecordListAdapter.TYPE_NO_DATA
                    transRecordList.add(noDataItem)
                }else{
                    transRecordList.addAll(tempList)
                }
                transRecordListAdapter.notifyDataSetChanged()
            }
            isReshuseDataing = false
        }
    }

    private fun dealRecord(
        isTest :Boolean = false,
        info: WalletMessage.GetTransactionResult?,
        sender: String?,
        tempList: MutableList<TransRecord>
    ) {
        if (info?.status == WalletMessage.ResponseStatus.SUCCESS) {
            val itemList =
                info?.transactionsList?.sortedByDescending { CommonUtil.formatMillis(it.txTime) }
            // 交易信息
            itemList?.forEachIndexed() { index, item ->
                val record = TransRecord()
                record.token_type = "ETH"
                record.time = CommonUtil.formatTimestamp(item.txTime)
                // 交易流水 item.txHash
                record.sent_hash = item.txHash
                record.testNet = isTest

                //trans_type
                if (sender.equals(item.from, true)) {
                    record.trans_type = "Transfer"
                    record.trans_type_code = TransRecord.TRANS_TYPE_CODE_TRANSFER
                    record.receiver_address = item.to
                } else if (sender.equals(item.to, true)) {
                    record.trans_type = "Receive"
                    record.trans_type_code = TransRecord.TRANS_TYPE_CODE_RECEIVE
                    record.receiver_address = item.from
                } else {
                    // 未知非法数据
                    return@forEachIndexed
                }

                // 数据
                val gasFee = CommonUtil.parseToDouble(item.gasFee)
                val txValue = CommonUtil.parseToDouble(item.txValue)
                record.fee_val = "${CommonUtil.formattedValue(gasFee)} ETH"
                record.amount_val = "${CommonUtil.formattedValue(txValue)} ETH"
                record.total_val = "${CommonUtil.formattedValue(gasFee + txValue)} ETH"

                // 状态
                if (item.transactionState == WalletMessage.TransactionState.TX_SUCCESS) {
                    record.trans_status = TransRecord.TRANS_STATUS_CODE_SUCCESS_TXT
                    record.trans_status_code = TransRecord.TRANS_STATUS_CODE_SUCCESS
                } else if (item.transactionState == WalletMessage.TransactionState.TX_FAILURE) {
                    record.trans_status = TransRecord.TRANS_STATUS_CODE_FAILED_TXT
                    record.trans_status_code = TransRecord.TRANS_STATUS_CODE_FAILED

                } else if (item.transactionState == WalletMessage.TransactionState.TX_PENDING) {
                    record.trans_status = TransRecord.TRANS_STATUS_CODE_PENDING_TXT
                    record.trans_status_code = TransRecord.TRANS_STATUS_CODE_PENDING
                } else {
                    record.trans_status = TransRecord.TRANS_STATUS_CODE_PENDING_TXT
                    record.trans_status_code = TransRecord.TRANS_STATUS_CODE_PENDING
                }

                when (transType) {
                    TRANS_TYPE_TRANSFER -> {
                        if (record.trans_type_code == TransRecord.TRANS_TYPE_CODE_TRANSFER) {
                            tempList.add(record)
                        }
                    }

                    TRANS_TYPE_RECEIVE -> {
                        if (record.trans_type_code == TransRecord.TRANS_TYPE_CODE_RECEIVE) {
                            tempList.add(record)
                        }
                    }

                    else -> {
                        tempList.add(record)
                    }
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        Log.d(GlobalConstant.APP_TAG, "TransContentFragment-->onResume")
        if (isFirstLoad){
            isFirstLoad = false
            reshuseData()
        }
    }

}