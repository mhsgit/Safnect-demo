package com.populstay.wallet.intent

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService

sealed class ConnectIntent {
    //连接
    data class Connect(val gatt: BluetoothGatt) : ConnectIntent()

    //关闭连接
    object DisConnect : ConnectIntent()

    //发现服务
    data class Discovered(val gattservices: List<BluetoothGattService>) : ConnectIntent()

    //写入数据
    data class WriteCharacteristic(val str: Any, val characteristic: BluetoothGattCharacteristic) : ConnectIntent()

    //消息信息
    data class ReadCharacteristic(val characteristic: BluetoothGattCharacteristic) : ConnectIntent()

    //消息信息
    data class CharacteristicNotify(val str: String, val characteristic: BluetoothGattCharacteristic) : ConnectIntent()

    //处理异常
    data class Error(val msg: String?) : ConnectIntent()
}