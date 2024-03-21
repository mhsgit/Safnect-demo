package com.populstay.wallet.State

import com.populstay.wallet.bean.CBleDevice

sealed class BleDeviceState {

    object Idle : BleDeviceState()
    object Loading : BleDeviceState()
    data class BleDevices(val devices: MutableList<CBleDevice>) : BleDeviceState()
    data class Error(val error: String?) : BleDeviceState()
}