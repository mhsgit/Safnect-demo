package com.populstay.wallet.device

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.populstay.wallet.FileUitl
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.R
import com.populstay.wallet.bean.CBleDevice
import com.populstay.wallet.databinding.ActivityDeviceListBinding
import com.populstay.wallet.device.adapter.DeviceAdapter
import com.populstay.wallet.devicemanager.DeviceConstant
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.mpc.IMpc
import com.populstay.wallet.proto.WalletMessage
import com.populstay.wallet.repository.BlueToothBLEUtil
import com.populstay.wallet.ui.ConnectFragment
import com.populstay.wallet.ui.loader.PeachLoader
import kotlinx.android.synthetic.main.activity_device_list.spinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DKGActivity : BTActivity() {

    companion object{
        const val TAG = "DeviceListActivity-->"
    }

    private lateinit var binding: ActivityDeviceListBinding

    private val mDataList by lazy {
        mutableListOf<BTBean>()
    }

    private val mDeviceAdapter by lazy {
        DeviceAdapter(this@DKGActivity,mDataList)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initTitleBar()
        initDeviceList()
        startScan()

        binding.pairMyWallet.setOnClickListener {
            showLoading(R.string.account_is_initializing)
            // 开始连接
            connect()
        }
    }

    override fun updateLoading(){
        PeachLoader.updateLoading("Account is initializing ($mCurrExeMsgCount/4)")
    }

    override fun onFoundService() {
        // 开始分片，刚连接上，要缓一会才能发起dkg
        binding.pairMyWallet.postDelayed({
            //startRunDKG()
            requestRunDKG()
        },500)
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

    override fun initTitleBar() {
        binding.commonTitleLayout.titleTv.text = getString(R.string.device_list_title)
        binding.commonTitleLayout.back.setOnClickListener {
            finish()
        }
    }

    private fun updateConfirmBtnStatus(){
        binding.pairMyWallet.isEnabled = null != mCurSelectDevice
    }

    override fun onBTRequestOpen() {
        startScan()
    }

    override fun onBTRequestPermissions() {
        startScan()
    }

    override fun onDKGCommonMsg(opRespStatus: Int) {
        super.onDKGCommonMsg(opRespStatus)
        PeachLogger.d(GlobalConstant.APP_TAG, "onDKGCommonMsg 副端同意创建账号 opRespStatus = $opRespStatus")
        if (WalletMessage.CommonResponseStatus.AGREE_FIELD_NUMBER == opRespStatus){
            startRunDKG()
        }
    }

    override fun onDKGOtherComplete() {
        super.onDKGOtherComplete()
        // 对方交易完成了，停止消息轮询
        // 流程结束，停止轮询
        onFlowPathComplete()
    }

    override fun onFlowPathComplete() {

        // 流程结束
        if (isFlowPathComplete()){
            stopLoading()
            stopQueryNewMessage()
            BlueToothBLEUtil.clearBtData()
            if(isFlowPathSuccess){
                toPairingSuccessfulActivity()
            }else{
                //账号初始化失败
                Toast.makeText(this@DKGActivity,R.string.pairing_fail,Toast.LENGTH_SHORT).show()
            }
        }
    }


    var requestRunDKGJob: Job? = null
    private fun requestRunDKG(){
        PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} requestRunDKG 发起创建账号申请")
        BlueToothBLEUtil.refreshDeviceCache()
        resetFlowPathStatus()
        stopQueryNewMessage()
        BlueToothBLEUtil.clearBtData()
        requestRunDKGJob?.cancel()
        requestRunDKGJob?.cancelChildren()
        requestRunDKGJob = lifecycleScope.launch {


            val characteristicCommonRead = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
            characteristicCommonRead?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristicCommonRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    BlueToothBLEUtil.setCharacteristicNotify(characteristicCommonRead , true)
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

                launch {

                    // 序列化对象
                    val paraProto = WalletMessage.CommonMsg.newBuilder()
                        .setType(WalletMessage.CommonMsgType.DKG_FIELD_NUMBER)
                        .setOpType(WalletMessage.OperateType.REQUEST_DKG_FIELD_NUMBER)
                        .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                        .build()
                        .toByteArray()

                    PeachLogger.d(GlobalConstant.APP_TAG, " 序列化对象paraProto = ${paraProto.size}")

                    characteristic.value = paraProto
                    BlueToothBLEUtil.writeCharacteristicSplit(characteristic,  paraProto)
                }
            }
        }
    }

    var startRunDKGJob: Job? = null
    private fun startRunDKG(){
        PeachLogger.d(GlobalConstant.APP_TAG, "startRunDKG 开始发起创建账号")
        stopQueryNewMessage()
        BlueToothBLEUtil.clearBtData()
        startRunDKGJob?.cancel()
        startRunDKGJob?.cancelChildren()
        startRunDKGJob = lifecycleScope.launch {

            // 发送消息分发私钥
            val characteristic = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_DKG))
            characteristic?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                }

                launch {
                    // 写入值
                    characteristic.value = BlueToothBLEUtil.BLECHARACTERISTIC_DKG.toByteArray()
                    //BlueToothBLEUtil.writeCharacteristic(characteristic, characteristic.value)
                    BlueToothBLEUtil.writeCharacteristicSplit(characteristic, characteristic.value)
                }
            }

            val characteristicNewMessageRead = mBluetoothGattService?.getCharacteristic(
                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
            characteristicNewMessageRead?.let {
                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                if (characteristicNewMessageRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                    //BlueToothBLEUtil.setCharacteristicNotify(characteristicNewMessageRead , true)
                }
            }

            // 执行DKG任务后，需要轮询新消息
            startQueryNewMessage()
            val dkg : WalletMessage.RunDKGResult?
            withContext(Dispatchers.IO) {
                // 创建
                val dkgResult = mpc.runDKG(
                    IMpc.ROLE_CLIENT,
                    FileUitl.getConfigDir()
                )
                val accountCreated = mpc.accountCreated(IMpc.ROLE_CLIENT,
                    FileUitl.getConfigDir())

                dkg = WalletMessage.RunDKGResult.parseFrom(dkgResult)
                mSelfComplete = true
                // 如果返回 null，表示在序列化消息的时候失败了
                PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} dkgResult=$dkgResult, dkg = ${dkg.status}, accountCreated= $accountCreated" )
                // 流程结束，停止轮询
                //stopQueryNewMessage()
                //BlueToothBLEUtil.clearBtData()
            }

            withContext(Dispatchers.Main){
                sendDkgComplete()
                PeachLogger.d(GlobalConstant.APP_TAG, "${BTActivity.TAG}  我方已经完成dkg")
                isFlowPathSuccess = WalletMessage.ResponseStatus.SUCCESS == dkg?.status
                onFlowPathComplete()
            }

        }

    }

    private var sendDkgCompleteJob: Job? = null
    private fun sendDkgComplete(){
        BlueToothBLEUtil.clearBtData()
        sendDkgCompleteJob?.cancel()
        sendDkgCompleteJob?.cancelChildren()
        sendDkgCompleteJob = lifecycleScope.launch {

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
                    .setType(WalletMessage.CommonMsgType.DKG_FIELD_NUMBER)
                    .setOpType(WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER)
                    .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                    .build().toByteArray()

                characteristic.value = paraProto
                BlueToothBLEUtil.writeCharacteristicSplit(characteristic, paraProto)
                // }
            }
        }
    }

    private fun toPairingSuccessfulActivity(){
        val intent = Intent(this, PairingSuccessfulActivity::class.java)
        intent.putExtra(PairingSuccessfulActivity.FORM_TYPE,PairingSuccessfulActivity.FORM_TYPE_DKG)
        startActivity(intent)
        finish()
    }
}