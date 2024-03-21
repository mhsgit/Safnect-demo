package com.populstay.wallet.device

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.populstay.wallet.CommonUtil
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.ObjectSizeCalculator
import com.populstay.wallet.R
import com.populstay.wallet.base.BaseActivity
import com.populstay.wallet.bean.CBleDevice
import com.populstay.wallet.devicemanager.BTGattCallback
import com.populstay.wallet.devicemanager.DeviceConstant
import com.populstay.wallet.devicemanager.DeviceManager
import com.populstay.wallet.devicemanager.IBTListener
import com.populstay.wallet.devicemanager.IScanResult
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.mpc.ImplMpc
import com.populstay.wallet.proto.WalletMessage
import com.populstay.wallet.repository.BlueToothBLEUtil
import com.populstay.wallet.repository.IBlueTooth
import com.populstay.wallet.ui.ConnectFragment
import com.populstay.wallet.ui.loader.LoaderStyle
import com.populstay.wallet.ui.loader.PeachLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BTActivity : BaseActivity(),IScanResult, BTGattCallback , IBTListener , CoroutineScope by MainScope() {
    
    companion object{
        const val TAG = "BTActivity-->"
        const val QUERY_NEW_MESSAGE_TIME_INTERVAL = 100L
    }

    protected fun showLoading(@androidx.annotation.StringRes text : Int ) {
        PeachLoader.showLoading(
            this,
            LoaderStyle.BallSpinFadeLoaderIndicator.name,
            text
        )
    }

    protected var mCurrExeMsgCount = 0

    protected open fun updateLoading() {

    }

    protected fun stopLoading() {
        PeachLoader.stopLoading()
    }

    protected var mSelfComplete = false
    protected var mOtherComplete = false
    protected var isFlowPathSuccess = false
    protected var transactionStatus : WalletMessage.ResponseStatus? = null
    protected var sentHash : String? = null

    fun resetFlowPathStatus(){
        sentHash = null
        transactionStatus = null
        isFlowPathSuccess = false
        mSelfComplete = false
        mOtherComplete = false
        mCurrExeMsgCount = 0
    }

    fun isFlowPathComplete() : Boolean{
        return mSelfComplete && mOtherComplete
    }

    protected var mCurSelectDevice : CBleDevice? = null
    protected var mBluetoothGattService : BluetoothGattService? = null
    protected val mpc by lazy {
        ImplMpc()
    }

    protected fun connect(){
        mCurSelectDevice?.device?.address?.let { address->
            // todo 特别的大坑，重复连接，导致蓝牙数据回传方法多次回调onCharacteristicChanged
            // todo  要先断开连接，才能二次连接，各种怀疑哎，kotlin协程问题、蓝牙传输问题。。。。。。
            DeviceManager.disconnect()
            DeviceManager.connect(address,this)
        }
    }

    protected fun startScan(){
        DeviceManager.startScanAndCheckBTStatus(this,this)
    }

    protected fun stopScan(){
        DeviceManager.stopScan()
    }

    // 消息轮询start
    val mHandler by  lazy {
        Handler()
    }
    fun startQueryNewMessage(delay : Long = QUERY_NEW_MESSAGE_TIME_INTERVAL){
        startQueryNewMessageJob?.cancel()
        startQueryNewMessageJob?.cancelChildren()
        stopQueryNewMessage()
        mHandler.postDelayed({
            startQueryNewMessageJob = lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    exeQueryNewMessage()
                }
            }
        },delay)
    }
    var startQueryNewMessageJob: Job? = null

    fun stopQueryNewMessage(){
        mHandler.removeCallbacksAndMessages(null)
    }

    suspend fun exeQueryNewMessage(){
        val msg = mpc.queryNewMessage()

        //PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} exeQueryNewMessage 消息转发，主端发出: msg = $msg, len = ${msg?.size}")
        // 如果返回 null，表示整个流程已经结束
        if (null == msg){
            //PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} exeQueryNewMessage 消息转发，主端发出: msg = $msg, len = ${msg?.size}")
            //stopQueryNewMessage()
            mSelfComplete = true
            mOtherComplete = true
            withContext(Dispatchers.Main){
                onFlowPathComplete()
            }
            return
        }else{
            // 反序列
            val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(msg)
            PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} exeQueryNewMessage 消息转发，主端发出: paraProto = $paraProto, message = ${paraProto.message}, status = ${paraProto.status}")
            if (paraProto.status != WalletMessage.ResponseStatus.SUCCESS){
                startQueryNewMessage(80)
                return
            }

            PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->主端发出-->data=${CommonUtil.calculateMD5(msg)}，size=${msg.size}")

            val objectSize = ObjectSizeCalculator.calculate(paraProto)

            PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} exeQueryNewMessage 消息转发，主端发出: msg = $msg, len = ${msg?.size},objectSize = $objectSize,paraProto =[${paraProto.status},${paraProto.message}]")
            // 转发给另一端:副端到主端-读
            val characteristic = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_WRITE))
            characteristic?.let {
                // 重新序列化
               /* val paraProto2 = WalletMessage.QueryMessageResult
                    .newBuilder()
                    .setMessage(paraProto.message)
                    .setStatus(paraProto.status)
                    .build().toByteArray()*/

                characteristic.value = msg
                //BlueToothBLEUtil.writeCharacteristic(characteristic,paraProto2)
                //lifecycleScope.launch {
                    BlueToothBLEUtil.writeCharacteristicSplit(characteristic,msg,object : IBlueTooth.IMsgSplitStatus{
                        override fun onIMsgSplitStatus() {
                            PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} exeQueryNewMessage onIMsgSplitStatus")

                            // 转发给另一端:副端到主端-读
                            startQueryNewMessage(900)
                        }

                    })
                //}
            }
        }
    }

    // 消息轮询end

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    var onConnectionStateChangeJob: Job? = null
    // 设备连接状态
    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        super.onConnectionStateChange(gatt, status, newState)
        val address = gatt?.device?.address
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client status = $status  newState = $newState address= $address")
        onConnectionStateChangeJob?.cancel()
        onConnectionStateChangeJob?.cancelChildren()
        onConnectionStateChangeJob = lifecycleScope.launch {
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return@launch
            }
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    BlueToothBLEUtil.refreshDeviceCache()
                    //连接状态
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            // 连接成功
                            gatt?.let {
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 连接ok,开始发现服务 address= $address")
                                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                                // 去发现服务
                                DeviceManager.discoverServices()
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            // 连接断开
                            gatt?.let {
                                // 重连
                                gatt.connect()
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 开始重连 address= $address")
                            }
                        }
                        else -> {
                            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 断开了 address= $address")
                            // 断开了
                            gatt?.disconnect()
                        }
                    }
                }
                else ->{
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client 连接失败了 address= $address")
                    onConnectionFail()
                }
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onMtuChanged Client mtu = $mtu")
    }

    var onServicesDiscoveredJob: Job? = null
    // 发现服务
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        onServicesDiscoveredJob?.cancel()
        onServicesDiscoveredJob?.cancelChildren()
        onServicesDiscoveredJob = lifecycleScope.launch {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    gatt?.let { gatt ->
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServicesDiscovered Client status = $status 发现服务")
                        gatt.getService(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLESERVER))?.let { service ->
                            mBluetoothGattService = service
                            val characteristicNewMessageRead = mBluetoothGattService?.getCharacteristic(
                                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
                            characteristicNewMessageRead?.let {
                                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                                if (characteristicNewMessageRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                                    BlueToothBLEUtil.setCharacteristicNotify(characteristicNewMessageRead , true)
                                }
                            }


                            val characteristicCommonRead = mBluetoothGattService?.getCharacteristic(
                                BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
                            characteristicCommonRead?.let {
                                // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                                if (characteristicCommonRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                                    BlueToothBLEUtil.setCharacteristicNotify(characteristicCommonRead , true)
                                }
                            }
                        }
                    }

                    launch {
                        //连接成功后设置MTU通讯
                        BlueToothBLEUtil.requestMTU(500)
                    }

                    // 回调给子类处理
                    onFoundService()
                }
                else -> {
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServicesDiscovered Client 发现服务失败")
                }
            }

        }
    }

    // 特征值变化，副端向主端传值
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicChanged Client value = $value ,characteristic = ${characteristic.uuid}")
        // todo 这里暂时看不到回调，后面再说吧
    }

    override fun onDKGCommonMsg(opRespStatus: Int) {
    }

    override fun onTransactionCommonMsg(opRespStatus: Int) {
    }

    override fun onDKGOtherComplete() {
        mOtherComplete = true
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onDKGOtherComplete 对方已经完成dkg currentThread = ${Thread.currentThread()}, isMainThread = ${isMainThread()}")
        // 注意这里要在主线程
    }

    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    override fun onTransactionOtherComplete() {
        mOtherComplete = true
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onTransactionComplete 对方已经完成交易 currentThread = ${Thread.currentThread()}, isMainThread = ${isMainThread()}")
    }


    var onCharacteristicChangedJob: Job? = null
    // 特征值变化，副端向主端传值
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
        characteristic?.let {

            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicChanged Old Client 消息转发，主端接受到:value = ${CommonUtil.calculateMD5(characteristic.value)} , len = ${characteristic?.value?.size},characteristic = ${characteristic?.uuid}")
        }
        onCharacteristicChangedJob?.cancel()
        onCharacteristicChangedJob?.cancelChildren()
        onCharacteristicChangedJob = lifecycleScope.launch {

            characteristic?.let {
                characteristic?.value?.let {
                    if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ)){


                        val res = gatt?.device?.address?.let { address ->
                            BlueToothBLEUtil.getDataTag(address,BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ)
                        }?.let { tag -> BlueToothBLEUtil.dealRecvByteArray(tag, it) }
                        PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->主端接受-->cur=${CommonUtil.calculateMD5(characteristic.value)}，size=${characteristic.value.size}")
                        try {
                            //接收完毕后进行数据处理
                            if(res == true) {
                                //获取接收完的数据
                                val recvByteArray = BlueToothBLEUtil.getRecvByteArray( BlueToothBLEUtil.getDataTag(gatt.device.address,BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
                                if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ)){
                                    PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->主端接受-->data=${CommonUtil.calculateMD5(recvByteArray)}，size=${recvByteArray.size}")
                                }
                                val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(recvByteArray)
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest notifyMessage= $paraProto, len = ${recvByteArray.size},paraProto = [${paraProto.status},${paraProto.message}]")

                                // 消息转发，副端到主端
                                val notifyState = mpc.notifyMessage(paraProto.message.toByteArray())
                                ++mCurrExeMsgCount
                                updateLoading()

                            } else {

                            }
                        }catch (e : Exception){
                            e.printStackTrace()
                            Log.e(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest notifyMessage= e = ${e.message}")
                        }
                    }else if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ)){
                        val res = gatt?.device?.address?.let { address ->
                            BlueToothBLEUtil.getDataTag(address,BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ)
                        }?.let { tag -> BlueToothBLEUtil.dealRecvByteArray(tag, it) }
                        PeachLogger.d(GlobalConstant.APP_TAG, "${ConnectFragment.TAG} 副端回复了")
                        try {
                            //接收完毕后进行数据处理
                            if(res == true) {
                                //获取接收完的数据
                                val recvByteArray = BlueToothBLEUtil.getRecvByteArray( BlueToothBLEUtil.getDataTag(gatt.device.address,BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))
                                val paraProto : WalletMessage.CommonMsg  = WalletMessage.CommonMsg.parseFrom(recvByteArray)
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest common msg = $paraProto, len = ${recvByteArray.size},paraProto = [${paraProto.type},${paraProto.opType}]")
                                when(paraProto.type){
                                    WalletMessage.CommonMsgType.DKG_FIELD_NUMBER ->{
                                        if (WalletMessage.OperateType.RESPONSE_DKG_FIELD_NUMBER == paraProto.opType){
                                            // 响应DKG请求
                                            onDKGCommonMsg(paraProto.opRespStatus)
                                        } else if (WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER == paraProto.opType){
                                            // DKG完成了
                                            onDKGOtherComplete()
                                        } else{

                                        }

                                    }
                                    WalletMessage.CommonMsgType.TRANSACTION_FIELD_NUMBER ->{
                                        if (WalletMessage.OperateType.RESPONSE_TRANSACTION_FIELD_NUMBER == paraProto.opType){
                                            // 响应交易请求
                                            onTransactionCommonMsg(paraProto.opRespStatus)
                                        }else if(WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER == paraProto.opType){
                                            // 对方交易完成了
                                            onTransactionOtherComplete()
                                        } else {

                                        }
                                    }

                                    else -> {}
                                }

                            } else {

                            }
                        }catch (e : Exception){
                            e.printStackTrace()
                            Log.e(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest common msg= e = ${e.message}")
                        }
                    } else {

                    }
                }
            }
            Log.e(GlobalConstant.APP_TAG, "$TAG onCharacteristicChangedJob 携程结束")
        }
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        // 打印一下而已，主端向副端写入数据回调
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWrite Client status = $status，characteristic = ${characteristic?.value?.toString(Charsets.UTF_8)},uuid = ${characteristic?.uuid}")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DeviceConstant.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                onBTRequestOpen()
            } else {
                // 用户未能成功打开蓝牙
                Toast.makeText(this@BTActivity, resources.getString(R.string.refused_turn_on_bluetooth), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (BlueToothBLEUtil.REQUEST_CODE_PERMISSIONS == requestCode) {
            for (x in grantResults) {
                if (x == PackageManager.PERMISSION_DENIED) {
                    //权限拒绝了
                    return
                }
            }
            onBTRequestPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopQueryNewMessage()
        stopLoading()
        stopScan()
        DeviceManager.clearBleDeviceList()
    }

}