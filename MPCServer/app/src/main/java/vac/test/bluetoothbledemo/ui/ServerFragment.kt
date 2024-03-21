package vac.test.bluetoothbledemo.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.populstay.wallet.proto.WalletMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vac.test.bluetoothbledemo.BuildConfig
import vac.test.bluetoothbledemo.CommonUtil
import vac.test.bluetoothbledemo.EncodeUtil
import vac.test.bluetoothbledemo.FileUitl
import vac.test.bluetoothbledemo.GlobalConstant
import vac.test.bluetoothbledemo.ObjectSizeCalculator
import vac.test.bluetoothbledemo.R
import vac.test.bluetoothbledemo.State.PhoneState
import vac.test.bluetoothbledemo.State.ServerState
import vac.test.bluetoothbledemo.bean.CPhone
import vac.test.bluetoothbledemo.databinding.FragmentServerBinding
import vac.test.bluetoothbledemo.intent.ServerIntent
import vac.test.bluetoothbledemo.log.PeachLogger
import vac.test.bluetoothbledemo.mpc.IMpc
import vac.test.bluetoothbledemo.mpc.ImplMpc
import vac.test.bluetoothbledemo.repository.BlueToothBLEUtil
import vac.test.bluetoothbledemo.repository.IBlueTooth
import vac.test.bluetoothbledemo.ui.loader.LoaderStyle
import vac.test.bluetoothbledemo.ui.loader.PeachLoader
import vac.test.bluetoothbledemo.vm.ServerViewModel
import java.io.File

class ServerFragment : Fragment() , CoroutineScope by MainScope(){

    private lateinit var binding: FragmentServerBinding

    companion object {
        const val TAG = "ServerFragment-->"
        fun newInstance() = ServerFragment()

        const val QUERY_NEW_MESSAGE_TIME_INTERVAL = 100L
    }

    private lateinit var serverViewModel: ServerViewModel

    private var mPhone = CPhone()
    private var mBluetoothDevice: BluetoothDevice? = null
    private val mpc by lazy {
        ImplMpc()
    }

    protected var mSelfComplete = false
    protected var mOtherComplete = false

    fun resetFlowPathStatus(){
        mSelfComplete = false
        mOtherComplete = false
        mCurrExeMsgCount = 0
    }

    fun isFlowPathComplete() : Boolean{
        return mSelfComplete && mOtherComplete
    }

    val mHandler by  lazy {
        Handler()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentServerBinding.inflate(inflater, container, false)
        binding.versionTv.text = "Version:${BuildConfig.VERSION_NAME}"
        binding.netTypeTv.text = "NetWork:${if (GlobalConstant.TEST_NET) "TestNet" else "MainNet"}"
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG Version:${BuildConfig.VERSION_NAME},NetWork:${if (GlobalConstant.TEST_NET) "TestNet" else "MainNet"}")
        return binding.root
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

    fun stopQueryNewMessage(){
       mHandler.removeCallbacksAndMessages(null)
    }

    var exeQueryNewMessageJob: Job? = null
     fun exeQueryNewMessage(){
        val msg = mpc.queryNewMessage()
        // 流程已经结束
        if (null == msg){
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG exeQueryNewMessage 消息转发，副端发出：msg = $msg , stopQueryNewMessage bull len =${msg?.size}")
            //if (isFlowPathComplete()){
                stopQueryNewMessage()
                stopLoading()
                BlueToothBLEUtil.clearBtData()
            //}
            //startQueryNewMessage(80)
        }else{
            val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(msg)
            if (paraProto.status != WalletMessage.ResponseStatus.SUCCESS){
                startQueryNewMessage(80)
                return
            }
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG GoLog  副端queryNewMessage到的SUCCESS消息,message=${paraProto.message}")
            val objectSize = ObjectSizeCalculator.calculate(paraProto)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG exeQueryNewMessage 消息转发，副端发出：msg = $msg , len =${msg.size},objectSize = $objectSize,paraProto =[${paraProto.status},${paraProto.message}]")
            // 转发给另一端:副端到主端-读
            val characteristic = BlueToothBLEUtil.getBluetoothGattService()?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
            characteristic?.let {
                mBluetoothDevice?.let {device ->
                    characteristic.value = msg
                    //BlueToothBLEUtil.writeCharacteristic(characteristic, msg)

                    val paraProto2 = WalletMessage.QueryMessageResult
                        .newBuilder()
                        .setMessage(paraProto.message)
                        .setStatus(paraProto.status)
                        .build().toByteArray()

                    PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->当前线程=${Thread.currentThread().id}")

                    PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->副端发出-->data=${CommonUtil.calculateMD5(msg)}，size=${msg.size}")

                    characteristic.value = msg
                    exeQueryNewMessageJob?.cancel()
                    exeQueryNewMessageJob?.cancelChildren()
                    exeQueryNewMessageJob = lifecycleScope.launch {
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(device,characteristic, msg,object : IBlueTooth.IMsgSplitStatus{
                            override fun onIMsgSplitStatus() {
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG exeQueryNewMessage onIMsgSplitStatus")
                                startQueryNewMessage(900)
                            }
                        })
                    }
                }
            }
        }
    }


    /**
     * 蓝牙广播回调类
     */
    private inner class advertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onStartSuccess BLE广播启动成功")
            //发送数据
            lifecycleScope.launch {
                //如果持续广播 则不提醒关闭
                if (BlueToothBLEUtil.Time != 0) {
                    lifecycleScope.async {
                        delay(BlueToothBLEUtil.Time.toLong())
                        serverViewModel.serverIntent.send(
                            ServerIntent.Info("BLE广播结束")
                        )
                    }
                }


                val advertiseInfo = StringBuffer("启动BLE广播成功")
                //连接性
                if (settingsInEffect.isConnectable) {
                    advertiseInfo.append(", 可连接")
                } else {
                    advertiseInfo.append(", 不可连接")
                }
                //广播时长
                if (settingsInEffect.timeout == 0) {
                    advertiseInfo.append(", 持续广播")
                } else {
                    advertiseInfo.append(", 广播时长 ${settingsInEffect.timeout} ms")
                }
                serverViewModel.serverIntent.send(
                    ServerIntent.Info(advertiseInfo.toString())
                )

            }
        }

        //具体失败返回码可以到官网查看
        override fun onStartFailure(errorCode: Int) {
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onStartFailure BLE广播启动失败 errorCode = $errorCode")
            //发送数据
            lifecycleScope.launch {
                var errstr = ""
                if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                    errstr = "启动Ble广播失败 数据报文超出31字节"
                } else {
                    errstr = "启动Ble广播失败 errorCode = $errorCode"
                }

                serverViewModel.serverIntent.send(
                    ServerIntent.Error(errstr)
                )
            }
        }
    }

    /**
     * GattServer回调
     */
    private inner class bluetoothGattServerCallback : BluetoothGattServerCallback() {

        //设备连接/断开连接回调
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Server status = $status  newState = $newState")
            lifecycleScope.launch {
                var msg = ""
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    //连接成功
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        mBluetoothDevice = device
                        msg = "${device.address} 连接成功"
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange 与${device.address} 连接成功")
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        msg = "${device.address} 断开连接"
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange 与${device.address} 断开连接")
                    }
                    serverViewModel.serverIntent.send(
                        ServerIntent.Info(msg)
                    )
                } else {
                    msg = "onConnectionStateChange status = $status newState = $newState"
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange 与${device.address} 连接失败")
                    serverViewModel.serverIntent.send(
                        ServerIntent.Error(msg)
                    )
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            super.onMtuChanged(device, mtu)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onMtuChanged mtu = $mtu")
            lifecycleScope.launch {
                BlueToothBLEUtil.mtuSize = mtu
                val msg = "通讯的MTU值改为${BlueToothBLEUtil.mtuSize}"
                serverViewModel.serverIntent.send(
                    ServerIntent.Info(msg)
                )
            }
        }

        //添加本地服务回调
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServiceAdded status = $status,UUUID = ${service.uuid}")
            lifecycleScope.launch {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServiceAdded 添加Gatt服务成功 UUUID = ${service.uuid}")
                    serverViewModel.serverIntent.send(
                        ServerIntent.Info("添加Gatt服务成功 UUUID = ${service.uuid}")
                    )
                } else {
                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onServiceAdded 添加Gatt服务失败 UUUID = ${service.uuid}")
                    serverViewModel.serverIntent.send(
                        ServerIntent.Error("添加Gatt服务失败")
                    )
                }
            }
        }

        //特征值读取回调
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicReadRequest requestId = $requestId")
            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return
            }
            BlueToothBLEUtil.sendResponse(
                device, requestId,
                offset, "我是服务端，你要提取数据给你了".toByteArray())

            lifecycleScope.launch {
                serverViewModel.serverIntent.send(
                    ServerIntent.Info(
                        "${device.address} 请求读取特征值:  UUID = ${characteristic.uuid} " +
                                "读取值 = ${EncodeUtil.bytesToHexString(characteristic.value)}"
                    )
                )

                //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                val readbytearray = "我是服务端，你要提取数据给你了".toByteArray()
                characteristic.value = readbytearray
                BlueToothBLEUtil.notifyCharacteristicChangedSplit(device,characteristic,readbytearray)
            }
        }

        //特征值写入回调
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            // 回调 onCharacteristicWriteRequest 函数时，需要调用下 mBtGattServer.sendResponse ，否则设备连接会莫名其妙断开。具体原因有待研究。
            BlueToothBLEUtil.sendResponse(device,requestId,offset,value)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest 消息转发，副端接受到 requestId = $requestId,  value = ${value} ，len =${value.size},uuid= ${characteristic.uuid}")

            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest not BLUETOOTH_CONNECT")
                return
            }
            dealCharacteristic(characteristic, device, value)

            // todo 屏蔽demo
            /*//刷新该特征值
            characteristic.value = "我是服务端，我写入数据完成".toByteArray()
            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return
            }

            BlueToothBLEUtil.sendResponse(
                device,requestId,offset,"我是服务端，我写入数据完成".toByteArray()
            )

            lifecycleScope.launch {

                serverViewModel.serverIntent.send(
                    ServerIntent.Info(
                        "${device.address} 请求写入特征值:  UUID = ${characteristic.uuid} " +
                                "写入值 = ${value.toString(Charsets.UTF_8)}"
                    )
                )

                //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                val readbytearray = "我是服务端，我写入数据完成".toByteArray()
                characteristic.value = readbytearray
                BlueToothBLEUtil.notifyCharacteristicChanged(device,characteristic,readbytearray)
            }*/


            // todo 分包逻辑先不处理了
           /* //处理接收的数据
            val res = BlueToothBLEUtil.dealRecvByteArray(device.address, value)

            //接收完毕后进行数据处理
            if(res) {
                //获取接收完的数据
                val recvByteArray = BlueToothBLEUtil.getRecvByteArray(device.address)

                var readstr = String(recvByteArray)
                lifecycleScope.launch {
                    serverViewModel.serverIntent.send(
                        ServerIntent.Info(
                            "${device.address} 请求写入特征值:  UUID = ${characteristic.uuid} " +
                                    "写入值 = ${readstr}"
                        )
                    )

                    lifecycleScope.async {
                        //模拟数据处理，延迟100ms
                        delay(100)

                        val sb = StringBuilder()
                        for(i in 1..10){
                            sb.append("服务端收到了客户端发的消息，这里是返回的消息,第${i}条 ")
                        }

                        val readbytearray = sb.toString().toByteArray()
                        characteristic.value = readbytearray

                        //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(device, characteristic, readbytearray)
                    }
                }
            }*/
        }

        //描述读取回调
        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onDescriptorReadRequest requestId = $requestId")
            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return
            }
            BlueToothBLEUtil.sendResponse(
                device, requestId,
                offset, descriptor.value)
            lifecycleScope.launch {
                ServerIntent.Info(
                    "${device.address} 请求读取描述值:  UUID = ${descriptor.uuid} " +
                            "读取值 = ${EncodeUtil.bytesToHexString(descriptor.value)}"
                )
            }
        }

        //描述写入回调
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onDescriptorWriteRequest requestId = $requestId")
            //刷新描述值
            descriptor.value = value
            // 响应客户端
            if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return
            }
            BlueToothBLEUtil.sendResponse(
                device, requestId,
                offset, value)

            lifecycleScope.launch {
                ServerIntent.Info(
                    "${device.address} 请求写入描述值:  UUID = ${descriptor.uuid} " +
                            "写入值 = ${EncodeUtil.bytesToHexString(value)}"
                )
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onNotificationSent status = $status")
            lifecycleScope.launch {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    serverViewModel.serverIntent.send(
                        ServerIntent.Info("${device?.address} 通知发送成功")
                    )
                } else {
                    serverViewModel.serverIntent.send(
                        ServerIntent.Error("${device?.address} 通知发送失败 status = $status")
                    )
                }
            }


        }
    }

    var dkgJob: Job? = null
    var sendTransactionJob: Job? = null
    var startQueryNewMessageJob: Job? = null
    var responseCompleteJob: Job? = null

    private fun dealCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        device: BluetoothDevice,
        value: ByteArray
    ) {

        // Common
        if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE)){
            val res = BlueToothBLEUtil.dealRecvByteArray(
                BlueToothBLEUtil.getDataTag(
                    device.address,
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE
                ), value
            )
            if (res) {

                //获取接收完的数据
                val recvByteArray = BlueToothBLEUtil.getRecvByteArray(
                    BlueToothBLEUtil.getDataTag(
                        device.address,
                        BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_WRITE
                    )
                )

                val paraProto : WalletMessage.CommonMsg  = WalletMessage.CommonMsg.parseFrom(recvByteArray)

                if(paraProto.type == WalletMessage.CommonMsgType.DKG_FIELD_NUMBER){
                    if (paraProto.opType == WalletMessage.OperateType.REQUEST_DKG_FIELD_NUMBER){
                        resetFlowPathStatus()
                        BlueToothBLEUtil.clearBtData()
                        // 展示DKG请求
                        showRequestDkgView(true,"Set up as a new device")
                    }else if (paraProto.opType == WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER){
                        mOtherComplete = true
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest 对方已经完成dkg")
                        if (isFlowPathComplete()){
                            responseCompleteJob = lifecycleScope.launch {
                                // delay(500)
                                withContext(Dispatchers.Main) {
                                    stopLoading()
                                    stopQueryNewMessage()
                                }
                                responseCompleteJob?.cancel()
                                responseCompleteJob?.cancelChildren()
                            }
                            BlueToothBLEUtil.clearBtData()
                        }
                    }
                }else if (paraProto.type == WalletMessage.CommonMsgType.TRANSACTION_FIELD_NUMBER){
                    if (paraProto.opType == WalletMessage.OperateType.REQUEST_TRANSACTION_FIELD_NUMBER){
                        resetFlowPathStatus()
                        BlueToothBLEUtil.clearBtData()
                        showRequestTransitionView(paraProto.transactionData)
                    }else if(paraProto.opType == WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER){
                        mOtherComplete = true
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest 对方已经完交易")
                        if (isFlowPathComplete()){
                            responseCompleteJob = lifecycleScope.launch {
                                // delay(500)
                                withContext(Dispatchers.Main) {
                                    stopLoading()
                                    stopQueryNewMessage()
                                }
                                responseCompleteJob?.cancel()
                                responseCompleteJob?.cancelChildren()
                            }
                            BlueToothBLEUtil.clearBtData()
                        }
                    }
                }


            }
        }
        // DKG-由主端发起
        else if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_DKG)) {
            val res = BlueToothBLEUtil.dealRecvByteArray(
                BlueToothBLEUtil.getDataTag(
                    device.address,
                    BlueToothBLEUtil.BLECHARACTERISTIC_DKG
                ), value
            )
            if (res) {

                //获取接收完的数据
                val recvByteArray = BlueToothBLEUtil.getRecvByteArray(
                    BlueToothBLEUtil.getDataTag(
                        device.address,
                        BlueToothBLEUtil.BLECHARACTERISTIC_DKG
                    )
                )

                dkgJob?.cancel()
                dkgJob?.cancelChildren()
                dkgJob = lifecycleScope.launch {


                    // 执行DKG任务后，需要轮询新消息
                    startQueryNewMessage()
                    var result = "0"
                    var dkg: WalletMessage.RunDKGResult? = null
                    withContext(Dispatchers.IO) {
                        val dkgResult: ByteArray? = mpc.runDKG(
                            IMpc.ROLE_SERVER,
                            File(context?.filesDir, "config").absolutePath
                        )
                        dkg = WalletMessage.RunDKGResult.parseFrom(dkgResult)

                        val accountCreated = mpc.accountCreated(
                            IMpc.ROLE_SERVER,
                            File(context?.filesDir, "config").absolutePath
                        )
                        PeachLogger.d(
                            GlobalConstant.APP_TAG,
                            "$TAG dkgResult= $dkgResult, dkg = ${dkg?.status},accountCreated=$accountCreated"
                        )

                        // 流程结束，停止轮询
                        //stopQueryNewMessage()
                        //BlueToothBLEUtil.clearBtData()

                        serverViewModel.serverIntent.send(
                            ServerIntent.Info(
                                "${device.address} 请求写入特征值:  UUID = ${characteristic.uuid} " +
                                        "写入值 = ${value.toString(Charsets.UTF_8)}"
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        responseDkgComplete()
                        mSelfComplete = true
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest 我方已经完成dkg")
                        if (isFlowPathComplete()){
                            stopLoading()
                            stopQueryNewMessage()
                            BlueToothBLEUtil.clearBtData()
                        }
                        // ok
                        if (dkg?.status == WalletMessage.ResponseStatus.SUCCESS) {
                            showRequestDkgView(false,"Your device is ready")
                            /*Toast.makeText(requireContext(), "初始化完成", Toast.LENGTH_SHORT)
                                .show()*/
                        } else {
                            showRequestDkgView(false,"Your device initialization failed")
                            // 失败
                           /* Toast.makeText(requireContext(), "初始化失败", Toast.LENGTH_SHORT)
                                .show()*/
                        }
                        //刷新该特征值 todo
                        //characteristic.value = result.toByteArray()
                        //BlueToothBLEUtil.sendResponse(device,requestId,offset,result.toByteArray())

                        //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                        val readbytearray = result.toByteArray()
                        characteristic.value = readbytearray
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(
                            device,
                            characteristic,
                            readbytearray
                        )
                    }
                }
            }
        } else if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_TRANSACTION)) {

            // 还没有创建好账户，不能交易
            if (!mpc.accountCreated(IMpc.ROLE_SERVER, FileUitl.getConfigDir())) {
                return
            }

            val res = BlueToothBLEUtil.dealRecvByteArray(
                BlueToothBLEUtil.getDataTag(
                    device.address,
                    BlueToothBLEUtil.BLECHARACTERISTIC_TRANSACTION
                ), value
            )
            if (res) {
                //获取接收完的数据
                val recvByteArray = BlueToothBLEUtil.getRecvByteArray(
                    BlueToothBLEUtil.getDataTag(
                        device.address,
                        BlueToothBLEUtil.BLECHARACTERISTIC_TRANSACTION
                    )
                )

                // 交易，由主端发起
                sendTransactionJob?.cancel()
                sendTransactionJob?.cancelChildren()
                sendTransactionJob = lifecycleScope.launch {
                    // 反序列化和解包
                    val paraProto: WalletMessage.SendTransactionPara =
                        WalletMessage.SendTransactionPara.parseFrom(recvByteArray)

                    PeachLogger.d(GlobalConstant.APP_TAG, "$TAG transaction= netType = ${paraProto.netType}")

                    // 执行DKG任务后，需要轮询新消息
                    startQueryNewMessage()
                    val testnet = paraProto.netType != 1
                    var transactionResult: WalletMessage.SendTransactionResult? = null
                    withContext(Dispatchers.IO) {
                        val transaction: ByteArray? = mpc.sendTransaction(
                            IMpc.ROLE_SERVER,
                            File(context?.filesDir, "config").absolutePath,
                            paraProto.receiver,
                            paraProto.amount,
                            "eth",
                            testnet
                        )
                        transactionResult = WalletMessage.SendTransactionResult.parseFrom(transaction)
                        PeachLogger.d(GlobalConstant.APP_TAG,
                            "$TAG transaction= $transaction, 交易结果transactionResult = ${transactionResult?.status},testnet = $testnet")

                        // 流程结束，停止轮询
                        //stopQueryNewMessage()
                        //BlueToothBLEUtil.clearBtData()

                     /*   serverViewModel.serverIntent.send(
                            ServerIntent.Info(
                                "${device.address} 请求写入特征值:  UUID = ${characteristic.uuid} " +
                                        "写入值 = ${recvByteArray.toString(Charsets.UTF_8)}"
                            )
                        )*/
                    }

                    withContext(Dispatchers.Main) {
                        responseTransitionComplete()
                        mSelfComplete = true
                        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest 我方已经完成交易")
                        if (isFlowPathComplete()){
                            stopLoading()
                            stopQueryNewMessage()
                            BlueToothBLEUtil.clearBtData()
                        }
                        //刷新该特征值 todo
                        //characteristic.value = result.toByteArray()
                        //BlueToothBLEUtil.sendResponse(device,requestId,offset,result.toByteArray())
                        if (transactionResult?.status == WalletMessage.ResponseStatus.SUCCESS){
                            showTransitionMsg("Processed Successfully",true)
                        }else{
                            showTransitionMsg("Processed Failed",true)
                        }

                        //回复客户端,让客户端读取该特征新赋予的值，获取由服务端发送的数据
                        val readbytearray = "result".toByteArray()
                        characteristic.value = readbytearray
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(
                            device,
                            characteristic,
                            readbytearray
                        )
                    }
                    sendTransactionJob?.cancel()
                    sendTransactionJob?.cancelChildren()
                }

            }
        } else if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_WRITE)) {

            try {
                val res = BlueToothBLEUtil.dealRecvByteArray(
                    BlueToothBLEUtil.getDataTag(
                        device.address,
                        BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_WRITE
                    ), value
                )
                PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->副端接受-->cur=${CommonUtil.calculateMD5(value)}，size=${value.size}")
                if (res) {
                    //获取接收完的数据
                    val recvByteArray = BlueToothBLEUtil.getRecvByteArray(
                        BlueToothBLEUtil.getDataTag(
                            device.address,
                            BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_WRITE
                        )
                    )
                    PeachLogger.d(GlobalConstant.APP_TAG, "数据通信-->副端接受-->data=${CommonUtil.calculateMD5(recvByteArray)}，size=${recvByteArray.size}")

                    // 反序列化和解包
                    val paraProto: WalletMessage.QueryMessageResult =
                        WalletMessage.QueryMessageResult.parseFrom(recvByteArray)
                    PeachLogger.d(
                        GlobalConstant.APP_TAG,
                        "$TAG onCharacteristicWriteRequest notifyMessage= $paraProto, len = ${recvByteArray.size} ,paraProto: status = ${paraProto.status}-----message=${paraProto.message}"
                    )

                    PeachLogger.d(
                        GlobalConstant.APP_TAG,
                        "$TAG GoLog  副端接受到主端发来的消息内容并执行notifyMessage,message=${paraProto.message}"
                    )

                    // 消息转发，主端到副端    0 表示成功，其他数字表示失败，可以按需重试
                    val notifyState = mpc.notifyMessage(paraProto.message.toByteArray())
                    ++mCurrExeMsgCount
                    updateLoading()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                PeachLogger.d(
                    GlobalConstant.APP_TAG,
                    "$TAG BLECHARACTERISTIC_NEW_MESSAGE_WRITE  ,e=${e.message}"
                )
            }

        } else if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC)) {



            val characteristic = BlueToothBLEUtil.getBluetoothGattService()?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_READ))
            characteristic?.let {
                mBluetoothDevice?.let {device ->
                    val vvvv = "这种情况很可能是因为 BLE 数据包长度的限制导致的。在 BLE 规范中，一个数据包的最大长度是 20 字节，如果要发送的数据长度超过 20 字节，就需要将数据拆分成多个数据包进行传输。\\n\" +\n" +
                            "                            \"\\n\" +\n" +
                            "                            \"在 Android 平台中，BLE 数据包默认的最大长度是 20 字节。因此，当主端发送 100 字节的数据时，这些数据就会被拆分成 5 个数据包进行传输。而由于每个数据包的长度不能超过 20 字节，可能存在一部分数据（如本例中的后 5 个字节）无法放入一个完整的数据包中，只能单独放在一个数据包中进行传输。\\n\" +\n" +
                            "                            \"\\n\" +\n" +
                            "                            \"接收方（副端设备）在接收到数据后，需要根据每个数据包的编号和总数对数据进行重组。通常情况下，若接收方 Android 设备支持 BLE 协议的所有协商特性，在交互开始前即尽可能完成了可靠的连接协议，那么它可以在数据包从主设备发送时通过设置适当的描述符来请求可靠的数据包传输。但是，如果接收方设备不支持所有指定的协商特性并且对您要处理的数据质量要求不高，那么有可能会丢失一些数据包。\\n\" +\n" +
                            "                            \"\\n\" +\n" +
                            "                            \"如果您需要确保所有的数据完整传输，可以考虑以下选择：\\n\" +\n" +
                            "                            \"\\n\" +\n" +
                            "                            \"将需要传输的数据分成不超过 20 字节的数据块，并间隔一段时间进行传输。\\n\" +\n" +
                            "                            \"使用 Android 平台的 BLE 分包传输功能，将要传输的数据分片然后使用 BluetoothGattCharacteristic.writeCharacteristic() 方法发送分块数据。\\n\" +\n" +
                            "                            \"将要传输的数据通过其他方式进行拆分，比如将数据压缩或编码成多个分块，然后再通过 BLE 协议进行传输这种情况很可能是因为 BLE 数据包长度的限制导致的。在 BLE 规范中，一个数据包的最大长度是 20 字节，如果要发送的数据长度超过 20 字节，就需要将数据拆分成多个数据包进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"在 Android 平台中，BLE 数据包默认的最大长度是 20 字节。因此，当主端发送 100 字节的数据时，这些数据就会被拆分成 5 个数据包进行传输。而由于每个数据包的长度不能超过 20 字节，可能存在一部分数据（如本例中的后 5 个字节）无法放入一个完整的数据包中，只能单独放在一个数据包中进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"接收方（副端设备）在接收到数据后，需要根据每个数据包的编号和总数对数据进行重组。通常情况下，若接收方 Android 设备支持 BLE 协议的所有协商特性，在交互开始前即尽可能完成了可靠的连接协议，那么它可以在数据包从主设备发送时通过设置适当的描述符来请求可靠的数据包传输。但是，如果接收方设备不支持所有指定的协商特性并且对您要处理的数据质量要求不高，那么有可能会丢失一些数据包。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"如果您需要确保所有的数据完整传输，可以考虑以下选择：\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将需要传输的数据分成不超过 20 字节的数据块，并间隔一段时间进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"使用 Android 平台的 BLE 分包传输功能，将要传输的数据分片然后使用 BluetoothGattCharacteristic.writeCharacteristic() 方法发送分块数据。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将要传输的数据通过其他方式进行拆分，比如将数据压缩或编码成多个分块，然后再通过 BLE 协议进行传输这种情况很可能是因为 BLE 数据包长度的限制导致的。在 BLE 规范中，一个数据包的最大长度是 20 字节，如果要发送的数据长度超过 20 字节，就需要将数据拆分成多个数据包进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"在 Android 平台中，BLE 数据包默认的最大长度是 20 字节。因此，当主端发送 100 字节的数据时，这些数据就会被拆分成 5 个数据包进行传输。而由于每个数据包的长度不能超过 20 字节，可能存在一部分数据（如本例中的后 5 个字节）无法放入一个完整的数据包中，只能单独放在一个数据包中进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"接收方（副端设备）在接收到数据后，需要根据每个数据包的编号和总数对数据进行重组。通常情况下，若接收方 Android 设备支持 BLE 协议的所有协商特性，在交互开始前即尽可能完成了可靠的连接协议，那么它可以在数据包从主设备发送时通过设置适当的描述符来请求可靠的数据包传输。但是，如果接收方设备不支持所有指定的协商特性并且对您要处理的数据质量要求不高，那么有可能会丢失一些数据包。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"如果您需要确保所有的数据完整传输，可以考虑以下选择：\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将需要传输的数据分成不超过 20 字节的数据块，并间隔一段时间进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"使用 Android 平台的 BLE 分包传输功能，将要传输的数据分片然后使用 BluetoothGattCharacteristic.writeCharacteristic() 方法发送分块数据。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将要传输的数据通过其他方式进行拆分，比如将数据压缩或编码成多个分块，然后再通过 BLE 协议进行传输这种情况很可能是因为 BLE 数据包长度的限制导致的。在 BLE 规范中，一个数据包的最大长度是 20 字节，如果要发送的数据长度超过 20 字节，就需要将数据拆分成多个数据包进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"在 Android 平台中，BLE 数据包默认的最大长度是 20 字节。因此，当主端发送 100 字节的数据时，这些数据就会被拆分成 5 个数据包进行传输。而由于每个数据包的长度不能超过 20 字节，可能存在一部分数据（如本例中的后 5 个字节）无法放入一个完整的数据包中，只能单独放在一个数据包中进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"接收方（副端设备）在接收到数据后，需要根据每个数据包的编号和总数对数据进行重组。通常情况下，若接收方 Android 设备支持 BLE 协议的所有协商特性，在交互开始前即尽可能完成了可靠的连接协议，那么它可以在数据包从主设备发送时通过设置适当的描述符来请求可靠的数据包传输。但是，如果接收方设备不支持所有指定的协商特性并且对您要处理的数据质量要求不高，那么有可能会丢失一些数据包。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"如果您需要确保所有的数据完整传输，可以考虑以下选择：\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将需要传输的数据分成不超过 20 字节的数据块，并间隔一段时间进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"使用 Android 平台的 BLE 分包传输功能，将要传输的数据分片然后使用 BluetoothGattCharacteristic.writeCharacteristic() 方法发送分块数据。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将要传输的数据通过其他方式进行拆分，比如将数据压缩或编码成多个分块，然后再通过 BLE 协议进行传输这种情况很可能是因为 BLE 数据包长度的限制导致的。在 BLE 规范中，一个数据包的最大长度是 20 字节，如果要发送的数据长度超过 20 字节，就需要将数据拆分成多个数据包进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"在 Android 平台中，BLE 数据包默认的最大长度是 20 字节。因此，当主端发送 100 字节的数据时，这些数据就会被拆分成 5 个数据包进行传输。而由于每个数据包的长度不能超过 20 字节，可能存在一部分数据（如本例中的后 5 个字节）无法放入一个完整的数据包中，只能单独放在一个数据包中进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"接收方（副端设备）在接收到数据后，需要根据每个数据包的编号和总数对数据进行重组。通常情况下，若接收方 Android 设备支持 BLE 协议的所有协商特性，在交互开始前即尽可能完成了可靠的连接协议，那么它可以在数据包从主设备发送时通过设置适当的描述符来请求可靠的数据包传输。但是，如果接收方设备不支持所有指定的协商特性并且对您要处理的数据质量要求不高，那么有可能会丢失一些数据包。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"如果您需要确保所有的数据完整传输，可以考虑以下选择：\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将需要传输的数据分成不超过 20 字节的数据块，并间隔一段时间进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"使用 Android 平台的 BLE 分包传输功能，将要传输的数据分片然后使用 BluetoothGattCharacteristic.writeCharacteristic() 方法发送分块数据。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将要传输的数据通过其他方式进行拆分，比如将数据压缩或编码成多个分块，然后再通过 BLE 协议进行传输这种情况很可能是因为 BLE 数据包长度的限制导致的。在 BLE 规范中，一个数据包的最大长度是 20 字节，如果要发送的数据长度超过 20 字节，就需要将数据拆分成多个数据包进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"在 Android 平台中，BLE 数据包默认的最大长度是 20 字节。因此，当主端发送 100 字节的数据时，这些数据就会被拆分成 5 个数据包进行传输。而由于每个数据包的长度不能超过 20 字节，可能存在一部分数据（如本例中的后 5 个字节）无法放入一个完整的数据包中，只能单独放在一个数据包中进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"接收方（副端设备）在接收到数据后，需要根据每个数据包的编号和总数对数据进行重组。通常情况下，若接收方 Android 设备支持 BLE 协议的所有协商特性，在交互开始前即尽可能完成了可靠的连接协议，那么它可以在数据包从主设备发送时通过设置适当的描述符来请求可靠的数据包传输。但是，如果接收方设备不支持所有指定的协商特性并且对您要处理的数据质量要求不高，那么有可能会丢失一些数据包。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"如果您需要确保所有的数据完整传输，可以考虑以下选择：\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将需要传输的数据分成不超过 20 字节的数据块，并间隔一段时间进行传输。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"使用 Android 平台的 BLE 分包传输功能，将要传输的数据分片然后使用 BluetoothGattCharacteristic.writeCharacteristic() 方法发送分块数据。\\\\n\\\" +\\n\" +\n" +
                            "                            \"                            \\\"将要传输的数据通过其他方式进行拆分，比如将数据压缩或编码成多个分块，然后再通过 BLE 协议进行传输。"
                    characteristic.value = vvvv.toByteArray()
                    exeQueryNewMessageJob?.cancel()
                    exeQueryNewMessageJob?.cancelChildren()
                    exeQueryNewMessageJob = lifecycleScope.launch {
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(device,characteristic, vvvv.toByteArray(),object : IBlueTooth.IMsgSplitStatus{
                            override fun onIMsgSplitStatus() {
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG exeQueryNewMessage onIMsgSplitStatus")
                               // startQueryNewMessage()
                            }
                        })
                    }
                }
            }
            //////////////////


//            PeachLogger.d(
//                GlobalConstant.APP_TAG,
//                "$TAG uuid = ${characteristic.uuid} 收到数据value size= ${value.size}, data= ${
//                    value.toString(Charsets.UTF_8)
//                }"
//            )
//            if (testDataTransStartTime == -1L) {
//                testDataTransStartTime = System.currentTimeMillis()
//            }
//            val res = BlueToothBLEUtil.dealRecvByteArray(
//                BlueToothBLEUtil.getDataTag(
//                    device.address,
//                    BlueToothBLEUtil.BLECHARACTERISTIC
//                ), value
//            )
//            if (res) {
//                //获取接收完的数据
//                val recvByteArray = BlueToothBLEUtil.getRecvByteArray(
//                    BlueToothBLEUtil.getDataTag(
//                        device.address,
//                        BlueToothBLEUtil.BLECHARACTERISTIC
//                    )
//                )
//                // 反序列化和解包
//                val endTime = (System.currentTimeMillis() - testDataTransStartTime) / 1000
//                testDataTransStartTime = -1
//                PeachLogger.d(
//                    GlobalConstant.APP_TAG,
//                    "$TAG uuid = ${characteristic.uuid}   接收完毕解析数据 耗时time = $endTime,totalSize= ${recvByteArray.size}, data= ${
//                        recvByteArray.toString(Charsets.UTF_8)
//                    }"
//                )
//            }

        }
    }


    //蓝牙广播回调类
    private lateinit var mAdvertiseCallback: advertiseCallback

    //GattServer回调
    private lateinit var mBluetoothGattServerCallback: BluetoothGattServerCallback

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serverViewModel = ViewModelProvider(requireActivity()).get(ServerViewModel::class.java)

        observeViewModel()

        initBluetooth()

        binding.btnstartAdvertising.setOnClickListener {
            startAdvertising()
        }

        binding.btnstopAdvertising.setOnClickListener {
            stopAdvertising()
        }

        binding.btnaddGattServer.setOnClickListener {
            addGattServer()
        }

        binding.btnwrite.setOnClickListener {

        }

        binding.btnread.setOnClickListener {

        }

        // 自动加入服务
        addGattServer()
        // 自动启动广播
        startAdvertising()

        initView()
    }

    private fun showLoading(@androidx.annotation.StringRes text : Int ) {
        PeachLoader.showLoading(
            requireContext(),
            LoaderStyle.BallSpinFadeLoaderIndicator.name,
            text
        )
    }

    protected var mCurrExeMsgCount = 0
    protected var mCurrOpType = 0

    protected fun updateLoading() {
        if(mCurrOpType == 0){
            PeachLoader.updateLoading("Account is initializing ($mCurrExeMsgCount/4)")
        }else{
            PeachLoader.updateLoading("Transaction in progress ($mCurrExeMsgCount/10)")
        }
    }

    private fun stopLoading() {
        PeachLoader.stopLoading()
    }

    private fun initView() {

        if (mpc.accountCreated(IMpc.ROLE_SERVER,FileUitl.getConfigDir())){
            initTransitionView()
            showTransitionMsg("Your device is ready",false)
        }else{
            initDkgView()
        }
    }

    private fun initViewSecord(isDkg: Boolean) {
        if (isDkg){
            initDkgView()
        }else{
            initTransitionView()
            showTransitionMsg("Your device is ready",false)
        }
    }


    private fun initDkgView() {
        binding.dkgView.visibility = View.VISIBLE
        binding.transitionView.visibility = View.GONE
        binding.dkgCCBtn.setOnClickListener {
            responseDkg()
        }
    }

    private fun showDkgMsg(msg : String){
        binding.dkgText.text = msg
    }

    private fun showDkgCCBtn(isShow : Boolean){
        binding.dkgCCBtn.visibility = if (isShow) View.VISIBLE else View.GONE
    }

    var showRequestDkgViewJob: Job? = null
    private fun showRequestDkgView(isShow : Boolean = false,msg :String,isInit : Boolean = false){
        showRequestDkgViewJob?.cancel()
        showRequestDkgViewJob?.cancelChildren()
        showRequestDkgViewJob = lifecycleScope.launch {
            if (isInit){
                stopQueryNewMessage()
            }
            initViewSecord(true)
            showDkgMsg(msg)
            showDkgCCBtn(isShow)
        }
    }

    var responseDkgJob: Job? = null
    private fun responseDkg(){
        showLoading(R.string.account_is_initializing)
        mCurrOpType = 0
        mCurrExeMsgCount = 0
        responseDkgJob?.cancel()
        responseDkgJob?.cancelChildren()
        responseDkgJob = lifecycleScope.launch {

            // 发送消息分发私钥
            val characteristic = BlueToothBLEUtil.getBluetoothGattService()?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))

            val device = mBluetoothDevice
            device?.let { device ->
                characteristic?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        //BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                    }

                    launch {

                        // 序列化对象
                        val paraProto = WalletMessage.CommonMsg
                            .newBuilder()
                            .setType(WalletMessage.CommonMsgType.DKG_FIELD_NUMBER)
                            .setOpType(WalletMessage.OperateType.RESPONSE_DKG_FIELD_NUMBER)
                            .setOpRespStatus(WalletMessage.CommonResponseStatus.AGREE_FIELD_NUMBER)
                            .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                            .build().toByteArray()

                        characteristic.value = paraProto
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(
                            device,
                            characteristic,
                            paraProto
                        )
                    }
                }
            }
        }

    }

    private var responseDkgCompleteJob: Job? = null
    private fun responseDkgComplete(){
        //showLoading(R.string.transaction_in_progress)
        responseDkgCompleteJob?.cancel()
        responseDkgCompleteJob?.cancelChildren()
        responseDkgCompleteJob = lifecycleScope.launch {

            // 发送消息分发私钥
            val characteristic = BlueToothBLEUtil.getBluetoothGattService()?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))

            val device = mBluetoothDevice
            device?.let { device ->
                characteristic?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        // BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                    }

                    //launch {

                    // 序列化对象
                    val paraProto = WalletMessage.CommonMsg
                        .newBuilder()
                        .setType(WalletMessage.CommonMsgType.DKG_FIELD_NUMBER)
                        .setOpType(WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER)
                        .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                        .build().toByteArray()

                    characteristic.value = paraProto
                    BlueToothBLEUtil.notifyCharacteristicChangedSplit(
                        device,
                        characteristic,
                        paraProto
                    )
                    //}
                }
            }
        }
    }

    private var responseTransitionJob: Job? = null
    private fun responseTransition(){
        showLoading(R.string.transaction_in_progress)
        mCurrOpType = 1
        mCurrExeMsgCount = 0
        responseTransitionJob?.cancel()
        responseTransitionJob?.cancelChildren()
        responseTransitionJob = lifecycleScope.launch {

            // 发送消息分发私钥
            val characteristic = BlueToothBLEUtil.getBluetoothGattService()?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))

            val device = mBluetoothDevice
            device?.let { device ->
                characteristic?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                       // BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                    }

                    //launch {

                        // 序列化对象
                        val paraProto = WalletMessage.CommonMsg
                            .newBuilder()
                            .setType(WalletMessage.CommonMsgType.TRANSACTION_FIELD_NUMBER)
                            .setOpType(WalletMessage.OperateType.RESPONSE_TRANSACTION_FIELD_NUMBER)
                            .setOpRespStatus(WalletMessage.CommonResponseStatus.AGREE_FIELD_NUMBER)
                            .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                            .build().toByteArray()

                        characteristic.value = paraProto
                        BlueToothBLEUtil.notifyCharacteristicChangedSplit(
                            device,
                            characteristic,
                            paraProto
                        )
                    //}
                }
            }
        }
    }

    private var responseTransitionCompleteJob: Job? = null
    private fun responseTransitionComplete(){
        //showLoading(R.string.transaction_in_progress)
        responseTransitionCompleteJob?.cancel()
        responseTransitionCompleteJob?.cancelChildren()
        responseTransitionCompleteJob = lifecycleScope.launch {

            // 发送消息分发私钥
            val characteristic = BlueToothBLEUtil.getBluetoothGattService()?.getCharacteristic(
                BlueToothBLEUtil.getUUID(
                    BlueToothBLEUtil.BLECHARACTERISTIC_COMMON_READ))

            val device = mBluetoothDevice
            device?.let { device ->
                characteristic?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                       // BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                    }

                    //launch {

                    // 序列化对象
                    val paraProto = WalletMessage.CommonMsg
                        .newBuilder()
                        .setType(WalletMessage.CommonMsgType.TRANSACTION_FIELD_NUMBER)
                        .setOpType(WalletMessage.OperateType.RESPONSE_COMPLETE_FIELD_NUMBER)
                        .setBackup("1") // proto对象必须传送有大小的值，单独传送enum，大小为0，无法蓝牙传输
                        .build().toByteArray()

                    characteristic.value = paraProto
                    BlueToothBLEUtil.notifyCharacteristicChangedSplit(
                        device,
                        characteristic,
                        paraProto
                    )
                    //}
                }
            }
        }
    }

    private fun initTransitionView() {
        binding.dkgView.visibility = View.GONE
        binding.transitionView.visibility = View.VISIBLE
        binding.transitionCCBtn.setOnClickListener {
            if (binding.transitionCCBtn.text == "Back"){
                showTransitionMsg("Your device is ready",false)
            }else{
                responseTransition()
            }
        }
    }

    private fun transitionInfoView(transInfo : WalletMessage.SendTransactionPara){
        binding.transitionText.visibility = View.GONE
        binding.transitionInfoView.visibility = View.VISIBLE
        binding.amountValTv.text = "ETH ${CommonUtil.formattedValue(transInfo.amount)}"
        binding.feesValTv.text = "ETH ${CommonUtil.formattedValue(transInfo.fee)}"
        binding.addressValTv.text = "${transInfo.receiver}"
    }
    private fun showTransitionMsg(msg : String,isOk : Boolean){
        //lifecycleScope.launch {
           if (isOk){
               binding.transitionCCBtn.visibility = View.VISIBLE
               binding.transitionCCBtn.text = "Back"
           }else{
               binding.transitionCCBtn.visibility = View.GONE
               binding.transitionCCBtn.text = "Ok"
           }
            binding.transitionText.visibility = View.VISIBLE
            binding.transitionInfoView.visibility = View.GONE
            binding.transitionText.text = msg
       // }
    }

    private fun showTransitionCCBtn(isShow : Boolean){
        binding.transitionCCBtn.visibility = if (isShow) View.VISIBLE else View.GONE
    }

    private var showRequestTransitionViewJob: Job? = null
    private fun showRequestTransitionView(transInfo : WalletMessage.SendTransactionPara){
        showRequestTransitionViewJob = lifecycleScope.launch {
           // delay(500)
            withContext(Dispatchers.Main) {
                stopQueryNewMessage()
                // 展示交易请求
                initViewSecord(false)
                transitionInfoView(transInfo)
                showTransitionCCBtn(true)
            }

            showRequestTransitionViewJob?.cancel()
            showRequestTransitionViewJob?.cancelChildren()
        }
    }


    fun show(msg :String){
        binding.showText.text = msg
    }

    fun getInput() :String{
        return binding.edtinput.text.toString()
    }

    var addGattServerJob: Job? = null
    private fun addGattServer(){
        addGattServerJob?.cancel()
        addGattServerJob?.cancelChildren()
        addGattServerJob = lifecycleScope.launch {
            try {
                BlueToothBLEUtil.addGattServer(mBluetoothGattServerCallback)
            } catch (e: Exception) {
                serverViewModel.serverIntent.send(ServerIntent.Error(e.message))
            }
        }
    }

    var stopAdvertisingJob: Job? = null
    private fun stopAdvertising(){
        /*stopAdvertisingJob?.cancel()
        stopAdvertisingJob?.cancelChildren()
        stopAdvertisingJob = lifecycleScope.launch {
            try {
                if (BlueToothBLEUtil.stopAdvertising(mAdvertiseCallback)) {
                    serverViewModel.serverIntent.send(ServerIntent.Info("停止Ble广播"))
                }
            } catch (e: Exception) {
                serverViewModel.serverIntent.send(ServerIntent.Error(e.message))
            }
        }*/

        try {
            if (BlueToothBLEUtil.stopAdvertising(mAdvertiseCallback)) {
               // serverViewModel.serverIntent.send(ServerIntent.Info("停止Ble广播"))
            }
        } catch (e: Exception) {
            //serverViewModel.serverIntent.send(ServerIntent.Error(e.message))
        }
    }

    var stopAdvertising2Job: Job? = null
    private fun startAdvertising(){
        stopAdvertising2Job?.cancel()
        stopAdvertising2Job?.cancelChildren()
        stopAdvertising2Job = lifecycleScope.launch {
            try {
                if (!BlueToothBLEUtil.startAdvertising(
                        "${mPhone.manufacturer} ${mPhone.modelname}",
                        mAdvertiseCallback
                    )
                ) {
                    serverViewModel.serverIntent.send(ServerIntent.Error("该手机芯片不支持BLE广播"))
                }
            } catch (e: Exception) {
                serverViewModel.serverIntent.send(ServerIntent.Error(e.message))
            }
        }
    }


    /**
     * 初始化蓝牙
     */
    private fun initBluetooth() {
        //初始化蓝牙回调包
        mAdvertiseCallback = advertiseCallback()
        //初始化GattServer回调
        mBluetoothGattServerCallback = bluetoothGattServerCallback()

        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG ble mac address:${BlueToothBLEUtil.getAddress()},device = ${android.os.Build.DEVICE},model= ${android.os.Build.MODEL},manufacturer = ${android.os.Build.MANUFACTURER}")
    }


    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    serverViewModel.serverState.collect {
                        when (it) {
                            is ServerState.Info -> {
                                binding.tvMsgShow.append("$it\r\n")
                            }

                            is ServerState.Error -> {
                                //错误用红色字体标识
                                val spannableString = SpannableString(it.msg)
                                spannableString.setSpan(
                                    ForegroundColorSpan(Color.RED),
                                    0,
                                    spannableString.length,
                                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                                )
                                binding.tvMsgShow.append("$spannableString\r\n")
                            }
                        }
                    }
                }
                launch {
                    serverViewModel.phone.collect {
                        when (it) {
                            is PhoneState.Idle -> {
                                mPhone = CPhone()
                            }

                            is PhoneState.Loading -> {

                            }

                            is PhoneState.Phone -> {
                                mPhone = it.device
                            }

                            is PhoneState.Error -> {
                              /*  Toast.makeText(requireContext(), it.error, Toast.LENGTH_SHORT)
                                    .show()*/
                            }

                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PeachLogger.d(GlobalConstant.APP_TAG, "$TAG onDestroy ")
        stopAdvertising()
        stopLoading()
        stopQueryNewMessage()
        BlueToothBLEUtil.clearBtData()
    }
}