package vac.test.bluetoothbledemo.mpc

interface IMpc {

    companion object{
        // 主端
        const val ROLE_CLIENT = "10001"
        // 副端
        const val ROLE_SERVER = "10002"
    }

    /**
       共用接口。不管是dkg还是 transaction，都需要调用这个接口
    - 功能：查询是否有新的消息需要转发。在 android 端启动对应的流程后，定时调用(10～30 ms 一次)这个接口查询是否有新的消息需要转发。如果返回的字符串不为空，就表明是一条需要转发的消息。该消息会被按照 protobuf 序列化，需要反序列化，然后发送给另外一端。
    - 返回值：序列化的proto消息。如果返回 null，表示整个流程已经结束。
    - 具体的 proto 看 github：https://github.com/bakey/tss-wallet/blob/main/protos/service.proto ， 反解出来QueryMessageResult 这个message
     */
    fun queryNewMessage () : ByteArray?

    /**
    - 功能：通知golang lib来处理新的消息，这个消息是从另外一端接收到的新消息
    - 输入参数：序列化的proto message
    - 返回：0 表示成功，其他数字表示失败，可以按需重试
     */
    fun  notifyMessage(message : ByteArray) : Int?


    /**
    - 功能：判断当前本机是否用户初次使用
    - 输入参数：
    - role：表明自己是主端还是副端，主端: "10001", 副端 "10002"
    - 返回：true 表示本机账号已创建过，false 表示还没有创建过(即为新用户)
     */
    fun accountCreated(role: String,data_path_prefix : String): Boolean?

    /**
    - 功能：发起一次 DKG，即密钥分发过程。这个函数为一个同步接口，可能要跑较长时间。最好把它放到后台线程去执行。 这个函数作为主端和副端共同的入口，也就是说，在手机端和在硬件端都是调用这个函数。
    - 输入参数
    - role：表明自己是主端还是副端，主端: "10001", 副端 "10002"
    - 返回
    - 返回消息：RunDKGResult的序列化结果。如果返回 null，表示在序列化消息的时候失败了
     */
    suspend fun runDKG(role : String, data_path_prefix : String) :ByteArray?

    /**
    - 功能：发起一次 transaction，这个函数为一个同步接口，可能要跑较长时间。最好把它放到后台线程去执行。 这个函数作为主端和副端共同的入口，也就是说，在手机端和在硬件端都是调用这个函数。
    - 输入参数
    - role：表明自己是主端还是副端，主端: "10001", 副端 "10002"
    - receiver: 接收币的地址，为一个十六进制的地址，例如："0xcb4d3f8d21335f9e41463d5966d7c794aec2534e"
    - Amount: 表示要发送币的数量，如果是以太坊，单位为 eth。其他币种待定
    - Currency: 表示币种, 默认为 以太坊。
    - 返回
    - 返回消息：SendTransactionResult 的序列化结果，具体可以参考service.proto。如果返回 nil，表示在序列化结果的时候失败
     */
    suspend fun sendTransaction(role :String, receiver : String, data_path_prefix : String,amount :Double, currency :String, testnet :Boolean) :ByteArray?

    suspend fun testFun() : String?

    /**
     * 查询当前账号持仓地址
     */
    suspend fun getMyAddress(role: String, data_path_prefix: String): String?

    /**
     * 查询持仓信息
     */
    suspend fun getMyAssets(sender: String): ByteArray?


    /**
     * 查询交易信息
     */
    suspend fun getTransactionList(address: String): ByteArray?

}