package com.populstay.wallet.State

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService

sealed class ConnectState {
    object Idle : ConnectState()
    data class Connect(val gatt: BluetoothGatt) : ConnectState()
    data class Discovered(val gattservices: MutableList<BluetoothGattService>) : ConnectState()
    data class Info(val info:String?) :ConnectState()
    data class Error(val error: String?) : ConnectState()
}