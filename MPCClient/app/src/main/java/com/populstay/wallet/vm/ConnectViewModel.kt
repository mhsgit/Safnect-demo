package com.populstay.wallet.vm

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import com.populstay.wallet.State.ConnectState
import com.populstay.wallet.intent.ConnectIntent
import com.populstay.wallet.repository.BlueToothBLEUtil

class ConnectViewModel : ViewModel() {

    val connectIntent = Channel<ConnectIntent>(Channel.UNLIMITED)

    private val _connectState = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val connectState: StateFlow<ConnectState>
        get() = _connectState

    //连接状态
    var isConnect = false
    var curGatt: BluetoothGatt? = null
    var gattservices = mutableListOf<BluetoothGattService>()

    init {
        initConnectIntent()
    }

    private fun initConnectIntent() {
        viewModelScope.launch {
            connectIntent.consumeAsFlow().collect {
                when (it) {
                    is ConnectIntent.Connect -> {
                        Log.i("pkg", "Connect ${it.gatt.device}")
                        curGatt = it.gatt
                        connectDevice(curGatt!!)
                        isConnect = true

                        //开始发现服务
                        BlueToothBLEUtil.discoverServices()
                    }

                    is ConnectIntent.Discovered -> {
                        gattservices = it.gattservices.toMutableList()
                        _connectState.value = ConnectState.Discovered(gattservices)
                    }

                    is ConnectIntent.DisConnect -> {
                        isConnect = false
                        _connectState.value = ConnectState.Idle
                    }

                    is ConnectIntent.WriteCharacteristic ->{

                        // 所有消息类型都走分包
                        launch {
                            val byteArray = if (it.str is String) it.str.toByteArray(charset = Charsets.UTF_8) else it.str
                            BlueToothBLEUtil.writeCharacteristicSplit(it.characteristic,  byteArray as ByteArray)
                        }

                        /*if (it.characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC_NEW_MESSAGE_WRITE)
                            || it.characteristic.uuid == BlueToothBLEUtil.getUUID(BlueToothBLEUtil.BLECHARACTERISTIC)){
                            launch {
                                val byteArray = if (it.str is String) it.str.toByteArray(charset = Charsets.UTF_8) else it.str
                                BlueToothBLEUtil.writeCharacteristicSplit(it.characteristic,  byteArray as ByteArray)
                            }
                        }else{
                            launch {
                                val byteArray = if (it.str is String) it.str.toByteArray(charset = Charsets.UTF_8) else it.str

                                BlueToothBLEUtil.writeCharacteristic(it.characteristic, byteArray as ByteArray)
                            }
                        }*/
                    }

                    is ConnectIntent.ReadCharacteristic ->{
                        val byteArray = BlueToothBLEUtil.readCharacteristic(it.characteristic)
                        byteArray?.let { bytes->
                            _connectState.value = ConnectState.Info(String(bytes))
                        }
                    }

                    is ConnectIntent.CharacteristicNotify ->{
                        _connectState.value = ConnectState.Info(it.str)
                    }

                    is ConnectIntent.Error ->{
                        _connectState.value = ConnectState.Error(it.msg)
                    }
                }
            }
        }
    }

    private fun connectDevice(gatt: BluetoothGatt) {
        _connectState.value = ConnectState.Connect(gatt)
    }
}