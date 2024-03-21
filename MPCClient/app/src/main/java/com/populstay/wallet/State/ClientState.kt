package com.populstay.wallet.State

import com.populstay.wallet.bean.CBleDevice

sealed class ClientState {
    object ScanMode : ClientState()
    object ConnectMode : ClientState()
    object DisConnect : ClientState()
    data class Connect(val dev: CBleDevice) : ClientState()
    data class Error(val error: String?) : ClientState()
}
