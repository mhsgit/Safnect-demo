package com.populstay.wallet.home.model.bean

import java.io.Serializable


open class TokenVH() : Serializable{
    open val itemType :Int = 0

    companion object{
        const val ITEM_TYPE_TOKEN_TOP = 1
        const val ITEM_TYPE_TOKEN = 2
    }
}

data class TokenTop(
    var label: String,
): TokenVH() {

    override val itemType: Int
        get() = ITEM_TYPE_TOKEN_TOP
}


data class Token(
    var label: String,
    var number: String,
    var amount: String,
    var icon : Int,
    var testNet : Boolean = false,
): TokenVH() {

    override val itemType: Int
        get() = ITEM_TYPE_TOKEN

}
