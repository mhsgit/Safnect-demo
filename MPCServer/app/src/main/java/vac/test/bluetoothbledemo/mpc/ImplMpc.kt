package vac.test.bluetoothbledemo.mpc

import com.populstay.wallet.proto.WalletMessage
import signer.Signer
import vac.test.bluetoothbledemo.CommonUtil
import vac.test.bluetoothbledemo.GlobalConstant
import vac.test.bluetoothbledemo.log.PeachLogger

class ImplMpc :IMpc{

    companion object{
        const val TAG = "ImplMpc-->"
        // 测试链条
        const val testnet = GlobalConstant.TEST_NET
    }

    override fun queryNewMessage(): ByteArray? {
        try {
            val queryNewMessage = Signer.queryNewMessage()
            if(null == queryNewMessage){
                PeachLogger.d(GlobalConstant.APP_TAG, "ImplMpc-->消息轮询，副端执行queryNewMessage消息，queryNewMessage 返回null")
            }else{
                val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(queryNewMessage)
                PeachLogger.d(GlobalConstant.APP_TAG, "ImplMpc-->消息轮询，副端执行queryNewMessage消息，status=${paraProto.status} ,message=${if(paraProto.message == null) "message 为 null" else CommonUtil.calculateMD5(
                    paraProto.message.toByteArray())}")
            }
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG queryNewMessage = $queryNewMessage currentThread = ${Thread.currentThread().id}")
            return queryNewMessage
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG queryNewMessage = ${e.message}")
        }
        return null
    }

    override fun notifyMessage(message: ByteArray): Int? {
        try {
            val notifyMessage = Signer.notifyMessage(message)
            PeachLogger.d(GlobalConstant.APP_TAG, "ImplMpc-->消息轮询，副端收到主端消息，执行notifyMessage，message=${CommonUtil.calculateMD5(message)}")
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG notifyMessage = $notifyMessage")
            return notifyMessage
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG notifyMessage = ${e.message}")
        }
        return null
    }

    override fun accountCreated(role: String,data_path_prefix : String): Boolean {
        try {
            val accountCreated = Signer.accountCreated(role,data_path_prefix)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG accountCreated = $accountCreated")
            return accountCreated
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG accountCreated = ${e.message}")
        }
        return false
    }

    override suspend fun runDKG(role: String, data_path_prefix : String): ByteArray? {
        try {
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG runDKG start")
            val runDKG = Signer.runDKG(role,data_path_prefix)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG runDKG = $runDKG")
            return runDKG
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG runDKG = ${e.message}")
        }
        return null
    }

    override suspend fun sendTransaction(
        role: String,
        receiver: String,
        data_path_prefix: String,
        amount: Double,
        currency: String,
        testnet: Boolean
    ): ByteArray? {
        try {
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG sendTransaction currentThread = ${Thread.currentThread().id}")
            val sendTransaction = Signer.sendTransaction(role,receiver,data_path_prefix,amount,currency,testnet)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG sendTransaction = $sendTransaction, length = ${sendTransaction.size}")
            return sendTransaction
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG sendTransaction = ${e.message}")
        }
        return null
    }

    override suspend fun testFun(): String? {
        try {
            // val sendTransaction = Signer.testFun()
            val sendTransaction = ""
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG testFun = $sendTransaction")
            return sendTransaction.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG testFun = ${e.message}")
        }
        return null
    }

    override suspend fun getMyAddress(role: String, data_path_prefix: String): String? {
        try {
            val getMyAddress = Signer.getMyAddress(role,data_path_prefix)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG getMyAddress = $getMyAddress")
            return getMyAddress
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG getMyAddress = ${e.message}")
        }
        return null
    }

    override suspend fun getMyAssets(sender: String): ByteArray? {
        try {
            val getMyAssets = Signer.getMyAssets(sender,testnet)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG getMyAssets = $getMyAssets")
            return getMyAssets
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG getMyAssets = ${e.message}")
        }
        return null
    }

    override suspend fun getTransactionList(address: String): ByteArray? {
        try {
            val getTransactionList = Signer.getTransactionList(address,testnet)
            PeachLogger.d(GlobalConstant.APP_TAG, "$TAG getTransactionList = $getTransactionList")
            return getTransactionList
        } catch (e: Exception) {
            e.printStackTrace()
            PeachLogger.e(GlobalConstant.APP_TAG, "$TAG getTransactionList = ${e.message}")
        }
        return null
    }
}