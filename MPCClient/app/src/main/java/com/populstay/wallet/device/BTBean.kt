package com.populstay.wallet.device

open class BTBean{

    companion object{
        const val TYPE_SCAN = 1
        const val TYPE_DEVICE = 0
    }

    var name :String? = null
    var type : Int = TYPE_DEVICE
}
