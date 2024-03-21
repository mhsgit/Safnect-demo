package com.populstay.wallet.ui

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.protobuf.Any
import com.populstay.wallet.CommonUtil
import com.populstay.wallet.FileUitl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.ObjectSizeCalculator
import com.populstay.wallet.R
import com.populstay.wallet.State.ClientState
import com.populstay.wallet.State.ConnectState
import com.populstay.wallet.adapter.GattServiceAdapter
import com.populstay.wallet.databinding.FragmentConnectBinding
import com.populstay.wallet.intent.ConnectIntent
import com.populstay.wallet.mpc.IMpc
import com.populstay.wallet.mpc.ImplMpc
import com.populstay.wallet.proto.WalletMessage
import com.populstay.wallet.repository.BlueToothBLEUtil
import com.populstay.wallet.vm.ClientViewModel
import com.populstay.wallet.vm.ConnectViewModel
import java.io.File
import java.util.Timer
import java.util.TimerTask


class ConnectFragment : BaseFragment<FragmentConnectBinding>() {

    companion object {
        const val TAG = "ConnectFragment-->"
        fun newInstance() = ConnectFragment()

        const val QUERY_NEW_MESSAGE_DELAY = 2000L
        const val QUERY_NEW_MESSAGE_TIME_INTERVAL = 15000L
    }

    private lateinit var connectViewModel: ConnectViewModel
    private lateinit var clientViewModel: ClientViewModel

    private lateinit var mAdapter: GattServiceAdapter
    private var mBluetoothGattService : BluetoothGattService? = null
    private var mBluetoothGatt : BluetoothGatt? = null

    //标记重置次数
    private var retryCount = 0
    private var isConnecting = false
    private var isCreating = false
    private var isTransactionning = false
    private val mpc by lazy {
        ImplMpc()
    }
    var timer : Timer? = null
    var timerTask  : TimerTask? =null

    fun startQueryNewMessage(){
        stopQueryNewMessage()
        // 在需要开始轮询任务的地方调用
        timerTask  = object : TimerTask() {
            override fun run() {
                // 执行轮询任务的代码
                exeQueryNewMessage()
            }
        }
        timer = Timer()
        timer?.schedule(timerTask, QUERY_NEW_MESSAGE_DELAY, QUERY_NEW_MESSAGE_TIME_INTERVAL)
    }

    fun stopQueryNewMessage(){
        timerTask?.cancel()
        timer?.cancel()
        timer = null
        timerTask = null
    }

    fun exeQueryNewMessage(){
        val msg = mpc.queryNewMessage()


        // 如果返回 null，表示整个流程已经结束
        if (null == msg){
            Log.d(GlobalConstant.APP_TAG, "$TAG exeQueryNewMessage 消息转发，主端发出: msg = $msg, len = ${msg?.size}")
            stopQueryNewMessage()
            return
        }else{
            // 反序列
            val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(msg)

            Log.d(GlobalConstant.APP_TAG, "$TAG GoLog  主端queryNewMessage到的SUCCESS消息,message=${paraProto.message}")

            if (paraProto.status != WalletMessage.ResponseStatus.SUCCESS){
                return
            }

            val objectSize = ObjectSizeCalculator.calculate(paraProto)

            Log.d(GlobalConstant.APP_TAG, "$TAG exeQueryNewMessage 消息转发，主端发出: msg = $msg, len = ${msg?.size},objectSize = $objectSize,paraProto =[${paraProto.status},${paraProto.message}]")
            // 转发给另一端:副端到主端-读
            val characteristic = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_WRITE))
            characteristic?.let {
                /*clientViewModel.curBleDevice.device?.let {device ->

                }*/

                // 重新序列化
                val paraProto2 = WalletMessage.QueryMessageResult
                    .newBuilder()
                    .setMessage(paraProto.message)
                    .setStatus(paraProto.status)
                    .build().toByteArray()

                characteristic.value = paraProto2
               // BlueToothBLEUtil.writeCharacteristic(characteristic,paraProto2)
                lifecycleScope.launch {
                    BlueToothBLEUtil.writeCharacteristicSplit(characteristic,paraProto2)
                }
            }
        }
    }

    override val bindingInflater: (LayoutInflater, ViewGroup?, Bundle?) -> FragmentConnectBinding
        get() = { layoutInflater, viewGroup, _ ->
            FragmentConnectBinding.inflate(layoutInflater, viewGroup, false)
        }

    /**
     * Gatt回调
     */
    val bleGattCallBack = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(GlobalConstant.APP_TAG, "$TAG onConnectionStateChange Client status = $status  newState = $newState")
            lifecycleScope.launch {
                if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    return@launch
                }
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        //连接状态
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            showConnecting(false)
                            gatt?.let {
                                connectViewModel.connectIntent.send(
                                    ConnectIntent.Connect(it)
                                )
                            }
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                            // 重连
                            gatt?.let {
                                showConnecting()
                                gatt.connect()
                            }
                        }
                        else {
                            gatt?.let {
                                it.disconnect()

                                connectViewModel.connectIntent.send(
                                    ConnectIntent.DisConnect
                                )
                            }
                        }
                    }

                    else -> {
                        connectViewModel.connectIntent.send(
                            ConnectIntent.DisConnect
                        )
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.d(GlobalConstant.APP_TAG, "$TAG onDescriptorWrite Client status = $status, uuid = ${descriptor?.uuid}")
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(GlobalConstant.APP_TAG, "$TAG onMtuChanged Client mtu = $mtu")
            lifecycleScope.launch {
                connectViewModel.connectIntent.send(
                    ConnectIntent.Error("通讯的MTU值修改：${mtu}")
                )

                binding.btnDo.postDelayed({ dealBluetoothGatt()},200)
            }
        }

        fun dealBluetoothGatt(){
            lifecycleScope.launch {
                mBluetoothGatt?.let { gatt ->
                    // todo 根据UUID获取服务
                    val currentServices = mutableListOf<BluetoothGattService>()
                    gatt.getService(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLESERVER))?.let { service ->
                        currentServices.add(service)



                        val characteristicNewMessageRead = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
                        characteristicNewMessageRead?.let {
                            // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                            if (characteristicNewMessageRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                                BlueToothBLEUtil.setCharacteristicNotify(characteristicNewMessageRead , true)
                            }
                        }
                    }



                    connectViewModel.connectIntent.send(
                        //ConnectIntent.Discovered(it.services)
                        ConnectIntent.Discovered(currentServices)
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(GlobalConstant.APP_TAG, "$TAG onServicesDiscovered Client status = $status")
            lifecycleScope.launch {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        mBluetoothGatt = gatt
                        launch {
                            //连接成功后设置MTU通讯
                            BlueToothBLEUtil.requestMTU(512)
                        }
                    }

                    else -> {
                        connectViewModel.connectIntent.send(
                            ConnectIntent.Error("发现服务失败！")
                        )
                    }
                }

            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            Log.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicRead Client status = $status, value = ${value.toString(Charsets.UTF_8)}")
            lifecycleScope.launch {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        val str = "返回消息：${String(value)}"
                        connectViewModel.connectIntent.send(
                            ConnectIntent.ReadCharacteristic(characteristic)
                        )
                    }
                    //无可读权限
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {

                    }

                    else -> {
                        connectViewModel.connectIntent.send(
                            ConnectIntent.Error("权限读取失败")
                        )
                    }
                }
            }

        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            characteristic?.let {
                Log.d(GlobalConstant.APP_TAG, "dealRecvByteArray=${CommonUtil.calculateMD5(it.value)}")
            }
            //Log.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicChanged Old Client 消息转发，主端接受到:value = ${characteristic?.value} , len = ${characteristic?.value?.size},characteristic = ${characteristic?.uuid}")
           /* lifecycleScope.launch {

                characteristic?.let {
                    characteristic?.value?.let {
                        if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ)){

                            //处理接收的数据
                            val res = gatt?.device?.address?.let { address ->
                                BlueToothBLEUtil.dealRecvByteArray(BlueToothBLEUtil.getDataTag(address,BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ), it)
                            }
                            if(res == true) {
                                val recvByteArray = BlueToothBLEUtil.getRecvByteArray(BlueToothBLEUtil.getDataTag(gatt.device.address,BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
                                val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(recvByteArray)
                                Log.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWriteRequest notifyMessage= $paraProto, len = ${recvByteArray.size},paraProto status = ${paraProto.status}-----message=${paraProto.message}")

                                Log.d(GlobalConstant.APP_TAG, "$TAG GoLog  主端接受到副端发来的消息内容并执行notifyMessage,message=${paraProto.message}")

                                // 消息转发，副端到主端  0 表示成功，其他数字表示失败，可以按需重试
                                val notifyState = mpc.notifyMessage(paraProto.message.toByteArray())
                            }
                        }
                    }
                }

            }*/
        }


        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            Log.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicChanged Client value = $value ,characteristic = ${characteristic.uuid}")
            lifecycleScope.launch {
                if (characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ)){
                    //处理接收的数据
                    val res = BlueToothBLEUtil.dealRecvByteArray( BlueToothBLEUtil.getDataTag(gatt.device.address,BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ), value)
                    //接收完毕后进行数据处理
                    if(res) {
                        //获取接收完的数据
                        val recvByteArray = BlueToothBLEUtil.getRecvByteArray( BlueToothBLEUtil.getDataTag(gatt.device.address,BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
                        val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(recvByteArray)

                        // 消息转发，副端到主端
                        mpc.notifyMessage(paraProto.message.toByteArray())

                        val str = "返回消息：${String(recvByteArray)}"
                        connectViewModel.connectIntent.send(
                            ConnectIntent.CharacteristicNotify(str, characteristic)
                        )
                    }
                }



            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(GlobalConstant.APP_TAG, "$TAG onCharacteristicWrite Client status = $status，characteristic = ${characteristic?.value?.toString(Charsets.UTF_8)},uuid = ${characteristic?.uuid}")

            // 服务端DKG结果
            if (characteristic?.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_DKG)){

            }


//            lifecycleScope.launch {
//                ConnectIntent.Error("${status} ")
//                characteristic?.let {
//                    if (BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
//                        gatt!!.readCharacteristic(it)
//                        ConnectIntent.Error(" ${String(it.value)}")
//                    }
//                }
//
//            }
        }


    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        connectViewModel = ViewModelProvider(requireActivity()).get(ConnectViewModel::class.java)
        clientViewModel = ViewModelProvider(requireActivity()).get(ClientViewModel::class.java)
        // TODO: Use the ViewModel
        observeViewModel()

        // todo 先不走列表形式
        //initRecyclerView()

        binding.btnDo.setOnClickListener {
            connectOrDisconnect()
        }

        // 创建账号
        binding.createAccountBtn.setOnClickListener {
            showCreating()
            lifecycleScope.launch {

                // 发送消息分发私钥
                val characteristic = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_DKG))
                characteristic?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                    }
                    connectViewModel.connectIntent.send(ConnectIntent.WriteCharacteristic(BlueToothBLEUtil.BLECHARACTERISTIC_DKG, it))

                }

                val characteristicNewMessageRead = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
                characteristicNewMessageRead?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristicNewMessageRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        BlueToothBLEUtil.setCharacteristicNotify(characteristicNewMessageRead , true)
                    }
                }

                // 执行DKG任务后，需要轮询新消息
                startQueryNewMessage()
                val dkgResult : ByteArray?
                withContext(Dispatchers.IO) {
                    // 创建
                     dkgResult = mpc.runDKG(IMpc.ROLE_CLIENT,
                        File(context?.filesDir, "config").absolutePath
                    )

                    val dkg = WalletMessage.RunDKGResult.parseFrom(dkgResult)
                    // 如果返回 null，表示在序列化消息的时候失败了
                    Log.d(GlobalConstant.APP_TAG, "$TAG dkgResult=$dkgResult, dkg = ${dkg.status}")
                    // 流程结束，停止轮询
                    stopQueryNewMessage()
                    BlueToothBLEUtil.clearBtData()
                }

                if (dkgResult == null){
                    binding.connectingText.text = "账号初始化失败"
                }else{
                    showAccountInfoView()
                }
            }
        }

        binding.splitBtn.setOnClickListener {

            lifecycleScope.launch {
                val characteristic = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC))

                val characteristic_red = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_READ))
                // 发送消息分发私钥
                characteristic_red?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic_red.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        BlueToothBLEUtil.setCharacteristicNotify(characteristic_red , true)
                    }
                    //it.value = paraProto
                    characteristic?.let {
                        connectViewModel.connectIntent.send(ConnectIntent.WriteCharacteristic("这种", characteristic))
                    }

                }
            }
        }

        // 转账
        binding.transactionBtn.setOnClickListener {
            lifecycleScope.launch {

                val receiver : String = binding.receiverEdit.text.toString()
                val amount: String = binding.amountEdit.text.toString()

                if (receiver.isEmpty()){
                    Toast.makeText(requireContext(),"请您输入转账地址",Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (amount.isEmpty()){
                    Toast.makeText(requireContext(),"请您输入转账金额",Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val amountDouble: Double = amount.toDouble()

                showTransactionning()

                // 序列化对象
                val paraProto = WalletMessage.SendTransactionPara
                    .newBuilder()
                    .setReceiver(receiver)
                    .setAmount(amountDouble)
                    .build().toByteArray()


                val characteristic = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_TRANSACTION))

                // 发送消息分发私钥
                characteristic?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        BlueToothBLEUtil.setCharacteristicNotify(characteristic , true)
                    }
                    it.value = paraProto
                    connectViewModel.connectIntent.send(ConnectIntent.WriteCharacteristic(paraProto, it))
                }

                val characteristicNewMessageRead = mBluetoothGattService?.getCharacteristic(BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_READ))
                characteristicNewMessageRead?.let {
                    // 通知的监听，这一步很关键，不然外围设备端发送的数据将无法接收到。
                    if (characteristicNewMessageRead.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){
                        BlueToothBLEUtil.setCharacteristicNotify(characteristicNewMessageRead , true)
                    }
                }

                // 执行sendTransaction任务后，需要轮询新消息
                startQueryNewMessage()
                val transaction : ByteArray?
                withContext(Dispatchers.IO) {
                    // 创建
                     transaction = mpc.sendTransaction(IMpc.ROLE_CLIENT, FileUitl.getConfigDir(),receiver,amountDouble,"eth")
                    val transactionResult = WalletMessage.SendTransactionResult.parseFrom(transaction)
                    // 如果返回 null，表示在序列化消息的时候失败了
                    Log.d(GlobalConstant.APP_TAG, "$TAG transaction=$transaction, 交易结果 transactionResult = ${transactionResult.status}")
                    // 流程结束，停止轮询
                    stopQueryNewMessage()
                    BlueToothBLEUtil.clearBtData()

                    // todo 交易成功
                    if(transactionResult.status == WalletMessage.ResponseStatus.SUCCESS){

                    }
                }

            }
        }

    }

    private fun initRecyclerView(){
        mAdapter = GattServiceAdapter(connectViewModel)
        mAdapter.setEmptyViewLayout(requireContext(), R.layout.rcl_gattservice)
        mAdapter.submitList(null)

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = mAdapter
    }

    private fun connectOrDisconnect(){
        lifecycleScope.launch {
            if (!connectViewModel.isConnect) {
                showConnecting()
                if (!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    return@launch
                }
                retryCount = 0
                Log.i("pkg", "${clientViewModel.curBleDevice.device?.name}")

                clientViewModel.curBleDevice.device?.let {
                    BlueToothBLEUtil.connect(
                        it.address,
                        bleGattCallBack
                    )
                }
            } else {
                showConnecting(false)
                BlueToothBLEUtil.disConnect()
                connectViewModel.connectIntent.send(
                    ConnectIntent.DisConnect
                )
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectViewModel.connectState.collect {
                        when (it) {
                            is ConnectState.Idle -> {
                                binding.btnDo.text = "手动连接"
                                binding.tvMsgShow.text = "当前状态:未连接"
                            }

                            is ConnectState.Connect -> {
                                binding.btnDo.text = "关闭连接"
                                binding.tvMsgShow.text = "当前状态:${it.gatt.device.name}已连接成功"
                            }

                            is ConnectState.Info -> {
                                binding.tvMsgShow.text = it.info
                            }

                            // 发现服务
                            is ConnectState.Discovered -> {
                                // todo 先不走列表形式
                                //mAdapter.submitList(it.gattservices)
                                // 获取当前的服务
                                mBluetoothGattService = it.gattservices[0]
                                showConnecting(false)
                                // 显示账号信息
                                showAccountInfoView()
                            }

                            is ConnectState.Error -> {
                                Toast.makeText(requireContext(), it.error, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
                launch {
                    clientViewModel.clientState.collect {
                        Log.i("pkg", "connectclientfragment ${it}")
                        when (it) {
                            is ClientState.ScanMode -> {
                                //replaceFragment(scanFragment)
                            }

                            is ClientState.ConnectMode -> {
                                //replaceFragment(connectFragment)
                            }

                            is ClientState.Connect -> {
                                binding.tvDeviceName.text = "目标设备:${clientViewModel.curBleDevice.device?.name}"
                                // 进入连接页面自动连接
                                connectOrDisconnect()
                            }

                            else -> {

                            }
                        }
                    }
                }
            }
        }
    }

    fun showConnecting(isShow : Boolean = true){
        isConnecting = isShow
        binding.connectingText.text = "正在连接设备..."
        binding.connectingText.visibility = if (isShow) View.VISIBLE else View.GONE
    }

    fun showCreating(isShow : Boolean = true){
        isCreating = isShow
        binding.connectingText.text = "正在初始化账号..."
        binding.connectingText.visibility = if (isShow) View.VISIBLE else View.GONE
    }

    fun showTransactionning(isShow : Boolean = true){
        isTransactionning = isShow
        binding.connectingText.text = "正在交易..."
        binding.connectingText.visibility = if (isShow) View.VISIBLE else View.GONE
    }


    fun showAccountInfoView(){
        if (isConnecting || isCreating){
            return
        }

        if (mpc.accountCreated(IMpc.ROLE_CLIENT,FileUitl.getConfigDir())){
            binding.accountInfo.visibility = View.VISIBLE
            binding.createAccountBtn.visibility = View.GONE
        }else{
            binding.accountInfo.visibility = View.GONE
            binding.createAccountBtn.visibility = View.VISIBLE
        }

        // todo populstay
    /*    binding.accountInfo.visibility = View.GONE
        binding.createAccountBtn.visibility = View.VISIBLE*/
        /*binding.accountInfo.visibility = View.VISIBLE
        binding.createAccountBtn.visibility = View.GONE*/
    }

    override fun onDestroy() {
        super.onDestroy()
        stopQueryNewMessage()
    }
}