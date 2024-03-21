package com.populstay.wallet.wallet.model.bean

import java.io.Serializable

data class NetWorkBean (
    var netName : String = NET_TYPE_MAIN_NAME,
    var netType : Int = NET_TYPE_MAIN
): Serializable
{
    companion object{
        const val NET_TYPE_MAIN = 1
        const val NET_TYPE_TEST = 2

        const val NET_TYPE_MAIN_NAME = "MainNet"
        const val NET_TYPE_TEST_NAME = "Sepolia"
    }
}