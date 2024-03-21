package com.populstay.wallet.wallet.model.bean

import com.populstay.wallet.home.model.bean.Token

 data class TransInfo (
     var crypto : MutableList<String>,
     var account : MutableList<Token>,
     var amount : Double,
     var available:Double,
     var availableTest:Double,
     var fee:Double,
     var unit:String,
     var address:String,
     var network:MutableList<NetWorkBean>
)