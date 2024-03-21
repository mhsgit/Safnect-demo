package com.populstay.wallet.wallet.model.bean

import java.io.Serializable


data class SelectTransInfo(
    var crypto : String = "",
    var account : String = "",
    var amount : Double = 0.0,
    var available:Double = 0.0,
    var availableTest :Double = 0.0,
    var fee:Double = 0.0,
    var unit:String = "ETH",
    var address:String = "",
    var network:NetWorkBean? = null
) : Serializable