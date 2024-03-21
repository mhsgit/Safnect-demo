package com.populstay.wallet.wallet.view

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.populstay.wallet.BaseApp
import com.populstay.wallet.CommonUtil
import com.populstay.wallet.FileUitl
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.R
import com.populstay.wallet.bean.CBleDevice
import com.populstay.wallet.dao.AppDatabase
import com.populstay.wallet.dao.DBUtils
import com.populstay.wallet.databinding.ActivityTransBinding
import com.populstay.wallet.device.BTActivity
import com.populstay.wallet.device.BTBean
import com.populstay.wallet.device.PairingSuccessfulActivity
import com.populstay.wallet.device.adapter.DeviceAdapter
import com.populstay.wallet.devicemanager.DeviceConstant
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.mpc.IMpc
import com.populstay.wallet.proto.WalletMessage
import com.populstay.wallet.repository.BlueToothBLEUtil
import com.populstay.wallet.transaction.model.bean.TransRecord
import com.populstay.wallet.ui.ConnectFragment
import com.populstay.wallet.ui.loader.PeachLoader
import com.populstay.wallet.wallet.model.bean.NetWorkBean
import com.populstay.wallet.wallet.model.bean.SelectTransInfo
import kotlinx.android.synthetic.main.activity_device_list.spinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransActivity : BTActivity() {

    companion object{
        const val TAG = "TransActivity-->"
    }

    private lateinit var binding: ActivityTransBinding

    private val mDataList by lazy {
        mutableListOf<BTBean>()
    }

    private val mDeviceAdapter by lazy {
        DeviceAdapter(this@TransActivity,mDataList)
    }

    private var mSelectData : SelectTransInfo? = null

    private var mTransRecord : TransRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initIntentData()
        initTitleBar()
        initDeviceList()
        startScan()

        binding.ransferConfirm.setOnClickListener {
            showLoading(R.string.transaction_in_progress)
            // 开始连接
            connect()
        }
    }

    private var initIntentDataJob: Job? = null
    private fun initIntentData() {
        initIntentDataJob?.cancel()
        initIntentDataJob?.cancelChildren()
        initIntentDataJob = lifecycleScope.launch {
            // 这里查费率吧
            var fee  = 0.0
            withContext(Dispatchers.IO) {
                val getEstimateGasFee = mpc.getEstimateGasFee()
                if (TextUtils.isEmpty(getEstimateGasFee) || TextUtils.isEmpty(getEstimateGasFee?.trim())){
                    fee = 0.0
                }else{
                    fee =  mpc.getEstimateGasFee()?.toDouble() ?: 0.0
                }
            }

            withContext(Dispatchers.Main){
                mSelectData = intent.getSerializableExtra(TransInfoActivity.TRANS_INFO) as SelectTransInfo?
                mSelectData?.let {
                    binding.receiverAddressTv.text = it.address
                    binding.amountValTv.text = "${it.unit} ${CommonUtil.formattedValue((it.amount))}"
                    binding.networkValTv.text = "${it.unit} ≈ ${CommonUtil.formattedValue((fee))}"
                    binding.totalValTv.text = "${it.unit} ${CommonUtil.formattedValue((it.amount + fee))}"
                    mSelectData?.fee = fee
                    updateConfirmBtnStatus()
                }
            }
        }
    }

    private fun updateConfirmBtnStatus(){
        binding.ransferConfirm.isEnabled = null != mSelectData && null != mCurSelectDevice
    }

    override fun initTitleBar() {
        binding.commonTitleLayout.titleTv.text = getString(R.string.trans_activity_title)
        binding.commonTitleLayout.back.setOnClickListener {
            finish()
        }
    }

    override fun updateLoading(){
        PeachLoader.updateLoading("Transaction in progress ($mCurrExeMsgCount/10)")
    }

    override fun onFoundService() {
        // 开始分片，刚连接上，要缓一会才能发起dkg
        binding.ransferConfirm.postDelayed({
            //startSendTransaction()
            requestSendTransaction()
        },500)
    }

    override fun onScanStatus(state: Int) {
        if (DeviceConstant.SCAN_STATUS_START == state){
            setScanningData()
        }
    }
    override fun onScanResult(deviceList: List<CBleDevice>?) {
        setDeviceData(deviceList)
        updateConfirmBtnStatus()
    }

    fun setScanningData(){
        mDataList.clear()
        val scanBean = BTBean()
        scanBean.name = "正在扫描"
        scanBean.type = BTBean.TYPE_SCAN
        mDataList.add(scanBean)
        mDeviceAdapter.notifyDataSetChanged()
        // 如果只有一项数据，则禁用 Spinner 的点击选择功能
        if (mDeviceAdapter.count == 1) {
            spinner.isEnabled = false;
            spinner.isClickable = false;
        }
    }

    fun setDeviceData(deviceList : List<BTBean>?){
        mCurSelectDevice = deviceList?.get(0) as CBleDevice?
        mDataList.clear()
        deviceList?.let {
            mDataList.addAll(it)
        }
        mDeviceAdapter.notifyDataSetChanged()
        spinner.isEnabled = true
        spinner.isClickable = true
    }

    private fun initDeviceList() {

        binding.spinner.adapter = mDeviceAdapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position)
                if (selectedItem is CBleDevice){
                    mCurSelectDevice = selectedItem
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 未选择任何项时的处理
            }
        }
    }

    override fun onBTRequestOpen() {
        startScan()
    }

    override fun onBTRequestPermissions() {
        startScan()
    }

    override fun onTransactionCommonMsg(opRespStatus: Int) {
        super.onTransactionCommonMsg(opRespStatus)
        if (WalletMessage.CommonResponseStatus.AGREE_FIELD_NUMBER == opRespStatus){
            startSendTransaction()
        }
    }

    private var requestSendTransactionJob: Job? = null
    private fun requestSendTransaction(){
        PeachLogger.d(GlobalConstant.APP_TAG, "startSendTransaction 发起交易申请")
        resetFlowPathStatus()
        stopQueryNewMessage()
        BlueToothBLEUtil.refreshDeviceCache()
        BlueToothBLEUtil.clearBtData()
        requestSendTransactionJob?.cancel()
        requestSendTransactionJob?.cancelChildren()
        requestSendTransactionJob = lifecycleScope.launch {

            val characteristicCommonRead = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
            characteristicCommonRead?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristicCommonRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                   // BlueToothBLEUtil.setCharacteristicNotify(characteristicCommonRead , true)
                }
            }


            // 发送消息分发私钥
            val characteristic = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE))
            characteristic?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                   // BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                }

               // launch {

                    // 交易信息
                    val receiver : String = mSelectData?.address ?: ""
                    val amountDouble: Double = mSelectData?.amount ?: 0.0
                    val feeDouble: Double = mSelectData?.fee ?: 0.0

                    // 序列化对象
                    val paraData = WalletMessage.SendTransactionPara
                        .newBuilder()
                        .setReceiver(receiver)
                        .setAmount(amountDouble)
                        .setFee(feeDouble)
                        .setNetType(mSelectData?.network?.netType ?: NetWorkBean.NET_TYPE_MAIN)
                        .build()

                    // 序列化对象
                    val paraProto = WalletMessage.CommonMsg
                        .newBuilder()
                        .setType(WalletMessage.CommonMsgType.TRANSACTION_FIELD_NUMBER)
                        .setOpType(WalletMessage.OperateType.REQUEST_TRANSACTION_FIELD_NUMBER)
                        .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                        .setTransactionData(paraData)
                        .build().toByteArray()

                    characteristic.value = paraProto
                    BlueToothBLEUtil.writeCharacteristicSplit(characteristic, paraProto)
               // }
            }
        }
    }

    private var sendTransactionCompleteJob: Job? = null
    private fun sendTransactionComplete(){
        BlueToothBLEUtil.clearBtData()
        sendTransactionCompleteJob?.cancel()
        sendTransactionCompleteJob?.cancelChildren()
        sendTransactionCompleteJob = lifecycleScope.launch {

            val characteristicCommonRead = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
            characteristicCommonRead?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristicCommonRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    //BlueToothBLEUtil.setCharacteristicNotify(characteristicCommonRead , true)
                }
            }


            // 发送消息分发私钥
            val characteristic = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE))
            characteristic?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                }

                // launch {

                // 序列化对象
                val paraProto = WalletMessage.CommonMsg
                    .newBuilder()
                    .setType(WalletMessage.CommonMsgType.TRANSACTION_FIELD_NUMBER)
                    .setOpType(WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER)
                    .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                    .build().toByteArray()

                characteristic.value = paraProto
                BlueToothBLEUtil.writeCharacteristicSplit(characteristic, paraProto)
                // }
            }
        }
    }

    override fun onTransactionOtherComplete() {
        super.onTransactionOtherComplete()
        // 对方交易完成了，停止消息轮询
        // 流程结束，停止轮询
        onFlowPathComplete()
    }

    override fun onConnectionFail() {

        Toast.makeText(this,resources.getString(R.string.connection_failed_scan_try),Toast.LENGTH_SHORT).show()

        resetFlowPathStatus()
        stopQueryNewMessage()
        BlueToothBLEUtil.clearBtData()
        stopLoading()

        stopScan()
        startScan()
    }

    override fun onFlowPathComplete() {
        // 流程结束
        if (isFlowPathComplete()){
            stopLoading()
            stopQueryNewMessage()
            BlueToothBLEUtil.clearBtData()
            if(null ==  transactionStatus){
                // null 不处理
                return
            }

            when (transactionStatus) {
                WalletMessage.ResponseStatus.SUCCESS -> {
                    toTransSuccessfulActivity()
                }
                WalletMessage.ResponseStatus.SEND_TXN_FAIL_INSUFFICIENT_BALANCE -> {
                    Toast.makeText(this@TransActivity,R.string.transaction_failed_balance_insufficient,Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this@TransActivity,R.string.transaction_failed,Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private var startSendTransactionJob: Job? = null
    private fun startSendTransaction(){
        PeachLogger.d(GlobalConstant.APP_TAG, "startSendTransaction 开始发起交易")
        stopQueryNewMessage()
        BlueToothBLEUtil.clearBtData()
        startSendTransactionJob?.cancel()
        startSendTransactionJob?.cancelChildren()
        startSendTransactionJob = lifecycleScope.launch {

            val receiver : String = mSelectData?.address ?: ""
            val amountDouble: Double = mSelectData?.amount ?: 0.0
            val fee: Double = mSelectData?.fee ?: 0.0

            // 序列化对象
            val paraProto = WalletMessage.SendTransactionPara
                .newBuilder()
                .setReceiver(receiver)
                .setAmount(amountDouble)
                .setFee(fee)
                .setNetType(mSelectData?.network?.netType ?: NetWorkBean.NET_TYPE_MAIN)
                .build().toByteArray()


            mTransRecord = TransRecord(token_type = "ETH",
                time = CommonUtil.formatTimeInMillis(System.currentTimeMillis()),
                testNet = mSelectData?.network?.netType == NetWorkBean.NET_TYPE_TEST,
                trans_type = "Transfer" ,
                trans_type_code =  TransRecord.TRANS_TYPE_CODE_TRANSFER,
                trans_status = TransRecord.TRANS_STATUS_CODE_PENDING_TXT,
                trans_status_code = TransRecord.TRANS_STATUS_CODE_PENDING,
                receiver_address = receiver,
                amount_val = "${CommonUtil.formattedValue(amountDouble)} ETH",
                fee_val = "${CommonUtil.formattedValue(fee)} ETH",
                total_val = "${CommonUtil.formattedValue(amountDouble + fee)} ETH"
            )

            val characteristic = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_TRANSACTION))

            // 发送消息分发私钥
            characteristic?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                }
              //  launch {
                    // 写入值
                    it.value = paraProto
                    BlueToothBLEUtil.writeCharacteristicSplit(it, paraProto)
               // }
            }

            val characteristicNewMessageRead = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
            characteristicNewMessageRead?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristicNewMessageRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    //BlueToothBLEUtil.setCharacteristicNotify(characteristicNewMessageRead , true)
                }
            }

            // 执行sendTransaction任务后，需要轮询新消息
            startQueryNewMessage()
            val transaction : ByteArray?
            val transactionResult : WalletMessage.SendTransactionResult?
            withContext(Dispatchers.IO) {
                PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->当前线程=${Thread.currentThread().id} transaction netType = ${mSelectData?.network?.netType}")
                val testnet = mSelectData?.network?.netType != 1
                // 创建
                transaction = mpc.sendTransaction(IMpc.ROLE_CLIENT, FileUitl.getConfigDir(),receiver,amountDouble,"eth",testnet)
                transactionResult = WalletMessage.SendTransactionResult.parseFrom(transaction)
                // 如果返回 null，表示在序列化消息的时候失败了
                PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} transaction=$transaction, 交易结果 transactionResult = ${transactionResult.status}, sentHash = ${transactionResult.sentHash}, testnet = $testnet")
                // 流程结束，停止轮询
                //stopQueryNewMessage()
               // BlueToothBLEUtil.clearBtData()

                // 交易记录入库,默认pending
                mTransRecord?.let {
                    mTransRecord?.sent_hash = transactionResult?.sentHash ?: ""
                    AppDatabase.getInstance(BaseApp.mContext).transRecordDao().insert(it)
                }
            }

            withContext(Dispatchers.Main){
                mSelfComplete = true
                sendTransactionComplete()
                PeachLogger.d(GlobalConstant.APP_TAG, "${BTActivity.TAG}  我方已经完成交易")
                transactionStatus = transactionResult?.status
                sentHash = transactionResult?.sentHash
                onFlowPathComplete()
            }
            PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->携程已经结束")
        }

    }

    private fun toTransSuccessfulActivity(){
        val intent = Intent(this, PairingSuccessfulActivity::class.java)
        intent.putExtra(PairingSuccessfulActivity.FORM_TYPE,PairingSuccessfulActivity.FORM_TYPE_TRANS)
        intent.putExtra(PairingSuccessfulActivity.SENT_HASH,sentHash)
        startActivity(intent)
        finish()
    }
}