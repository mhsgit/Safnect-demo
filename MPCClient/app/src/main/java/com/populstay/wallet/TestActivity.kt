package com.populstay.wallet

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.populstay.wallet.databinding.ActivityTestBinding
import com.populstay.wallet.mpc.IMpc
import com.populstay.wallet.mpc.ImplMpc
import com.populstay.wallet.proto.WalletMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File


class TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding
    companion object{
        const val mpcFile :String = "config"
        const val mpcServerFile :String = "configServer"
    }


    private val mpc by lazy {
        ImplMpc()
    }

    private val mpcServer by lazy {
        ImplMpc()
    }

    var queryNewMessageByteArray : ByteArray? = null
    var queryNewMessageByteArrayServer : ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initListener()
    }

    private fun initListener() {
        // 主端部分
        binding.dkg.setOnClickListener {

            lifecycleScope.launch {

                withContext(Dispatchers.IO) {
                    Log.d(GlobalConstant.APP_TAG, "MPC-->主端-->开始runDKG")
                    // 创建
                    val dkgResult = mpc.runDKG(
                        IMpc.ROLE_CLIENT,
                        FileUitl.getConfigDir()
                    )

                    val dkg = WalletMessage.RunDKGResult.parseFrom(dkgResult)
                    // 如果返回 null，表示在序列化消息的时候失败了
                    Log.d(GlobalConstant.APP_TAG, "MPC-->主端-->runDKG结束 dkgResult=$dkgResult, dkg = ${dkg.status}")
                }
            }
        }
        binding.queryNewMessage.setOnClickListener {
            queryNewMessageByteArray = mpc.queryNewMessage()
            val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(queryNewMessageByteArray)
            Log.d(GlobalConstant.APP_TAG, "MPC-->主端-->触发一次queryNewMessage，结果queryNewMessageByteArray=$queryNewMessageByteArray,status = ${paraProto.status},message= ${paraProto.message}")
        }
        binding.notifyMessage.setOnClickListener {
            Log.d(GlobalConstant.APP_TAG, "MPC-->主端-->触发一次notifyMessage，传入queryNewMessageByteArray=$queryNewMessageByteArrayServer")
            queryNewMessageByteArrayServer?.let {
                val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(queryNewMessageByteArrayServer)//.unpack(WalletMessage.QueryMessageResult::class.java)
                Log.d(GlobalConstant.APP_TAG, "MPC-->主端-->触发一次notifyMessage,status=${paraProto.status}")
                if (paraProto.status != WalletMessage.ResponseStatus.SUCCESS){
                    return@setOnClickListener
                }
                val result = mpc.notifyMessage(paraProto.message.toByteArray())
                Log.d(GlobalConstant.APP_TAG, "MPC-->主端-->触发notifyMessage，result=$result")
            }
        }
        binding.accountCreated.setOnClickListener {
            val result = mpc.accountCreated(IMpc.ROLE_CLIENT,FileUitl.getConfigDir())
            Log.d(GlobalConstant.APP_TAG, "MPC-->主端-->触发accountCreated，result=$result")
        }

        // 副端部分
        binding.dkgServer.setOnClickListener {

            lifecycleScope.launch {

                withContext(Dispatchers.IO) {
                    Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->开始runDKG")
                    // 创建
                    val dkgResult = mpcServer.runDKG(
                        IMpc.ROLE_SERVER,
                        File(this@TestActivity?.filesDir, mpcServerFile).absolutePath
                    )

                    val dkg = WalletMessage.RunDKGResult.parseFrom(dkgResult)
                    // 如果返回 null，表示在序列化消息的时候失败了
                    Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->runDKG结束 dkgResult=$dkgResult, dkg = ${dkg.status}")
                }
            }
        }
        binding.queryNewMessageServer.setOnClickListener {
            queryNewMessageByteArrayServer = mpcServer.queryNewMessage()
            val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(queryNewMessageByteArrayServer)
            Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->触发一次queryNewMessage，结果queryNewMessageByteArray=$queryNewMessageByteArrayServer,status = ${paraProto.status},message= ${paraProto.message}")
        }
        binding.notifyMessageServer.setOnClickListener {
            Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->触发一次notifyMessage，传入queryNewMessageByteArray=$queryNewMessageByteArray")
            queryNewMessageByteArray?.let {
                val paraProto : WalletMessage.QueryMessageResult  = WalletMessage.QueryMessageResult.parseFrom(queryNewMessageByteArray)//.unpack(WalletMessage.QueryMessageResult::class.java)
                Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->触发一次notifyMessage,status=${paraProto.status}")
                if (paraProto.status != WalletMessage.ResponseStatus.SUCCESS){
                    return@setOnClickListener
                }
                val result = mpcServer.notifyMessage(paraProto.message.toByteArray())
                Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->触发notifyMessage，result=$result")
            }
        }
        binding.accountCreatedServer.setOnClickListener {
            val result = mpcServer.accountCreated(IMpc.ROLE_SERVER,File(this@TestActivity?.filesDir, mpcServerFile).absolutePath)
            Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->触发accountCreated，result=$result")
        }

        binding.testTransaction.setOnClickListener {
           // postData()
            lifecycleScope.launch {

                Log.d(GlobalConstant.APP_TAG, "MPC-->副端-->触发accountCreated，result=${System.currentTimeMillis()}")

                var dkgResult : ByteArray? = null
                withContext(Dispatchers.IO) {
                    dkgResult = mpc.runDKG(IMpc.ROLE_CLIENT,File(this@TestActivity?.filesDir, "config").absolutePath)
                }

                withContext(Dispatchers.Main){
                    Toast.makeText(this@TestActivity,"接口调用结果：$dkgResult",Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    fun postData() {

        lifecycleScope.launch {

            var dkgResult : JSONObject? = null
            withContext(Dispatchers.IO) {

                try {
                    val contentType: MediaType? =
                        "application/json;charset=utf-8".toMediaTypeOrNull()
                    val jsonObject = JSONObject()
                    jsonObject.put("jsonrpc", "2.0")
                    jsonObject.put("id", 1)
                    jsonObject.put("method", "eth_getTransactionCount")

                    val jsonArray = JSONArray()
                    jsonArray.put("0x37B9F4E7580AAF315d97318540Bf2e06b3A75d19")
                    jsonArray.put("latest")
                    jsonObject.put("params",jsonArray)

                    val body: RequestBody =
                        RequestBody.create(contentType,jsonObject.toString()) //初始化请求体
                    val request: Request = Request.Builder()
                        .url("https://eth-mainnet.g.alchemy.com/v2/GEn0Q7Fp6lx7Q4gWjJY0-mfnikUrtk_w")
                        .post(body).build() //初始化post请求（注意与get的区别）
                    val client = OkHttpClient()
                    val response: Response = client.newCall(request).execute() //初始化响应结果对象并执行请求
                    val result: String? = response.body?.string()
                    val resJson = JSONObject(result) //解析数据
                    dkgResult = resJson
                    val success = resJson.getBoolean("success")
                    val message = resJson.getString("message")
                    val code = resJson.getInt("code")

                    Toast.makeText(this@TestActivity,"接口调用结果：${resJson.toString()}",Toast.LENGTH_LONG).show()
                } catch (exception: Exception) {
                    Log.e(GlobalConstant.APP_TAG, exception.toString())
                } finally {
                }
            }

            withContext(Dispatchers.Main){
                Toast.makeText(this@TestActivity,"接口调用结果：$dkgResult",Toast.LENGTH_LONG).show()
            }
        }
    }


}