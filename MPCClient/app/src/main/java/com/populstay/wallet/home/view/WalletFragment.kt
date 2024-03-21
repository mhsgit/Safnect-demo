package com.populstay.wallet.home.view

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.populstay.wallet.CommonUtil
import com.populstay.wallet.FileUitl
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.R
import com.populstay.wallet.databinding.FragmentWalletBinding
import com.populstay.wallet.eventbus.Event
import com.populstay.wallet.home.model.bean.Token
import com.populstay.wallet.home.model.bean.TokenTop
import com.populstay.wallet.home.model.bean.TokenVH
import com.populstay.wallet.home.view.adapter.RecyclerViewAdapter
import com.populstay.wallet.log.PeachLogger
import com.populstay.wallet.mpc.IMpc
import com.populstay.wallet.mpc.ImplMpc
import com.populstay.wallet.proto.WalletMessage
import com.populstay.wallet.ui.BaseFragment
import com.populstay.wallet.wallet.view.TransInfoActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class WalletFragment : BaseFragment<FragmentWalletBinding>() {

    private var tokenList: ArrayList<TokenVH> = ArrayList()
    private var tokenCount : Double = 0.0
    private lateinit var tokenListAdapter: RecyclerViewAdapter
    private val mpc by lazy {
        ImplMpc()
    }


    companion object {
        fun newInstance() = WalletFragment()
        const val TOKEN_INFO = "token_info"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override val bindingInflater: (LayoutInflater, ViewGroup?, Bundle?) -> FragmentWalletBinding
        get() = { layoutInflater, viewGroup, _ ->
            FragmentWalletBinding.inflate(layoutInflater, viewGroup, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun initView() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }

        binding.transferBtn.setOnClickListener {
            toTrans()
        }

        binding.receiveBtn.setOnClickListener {
            toReceive()
        }

        initData()

        binding.refreshLayout.setColorSchemeColors(resources.getColor(R.color.color_ff0b0bd8))
        binding.refreshLayout.setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener {
            binding.refreshLayout.post {
                binding.refreshLayout.isRefreshing = true
                initAddressInfo()
            }
        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Event?) {
        if (null == event || event.type <= 0) {
            PeachLogger.d("onEvent--无效事件")
            return
        }

        when(event.type){
            Event.EventType.BACK_WALLET ->{
                initAddressInfo()
            }
        }
    }

    private fun initData() {
        tokenList.clear()
        tokenList.add(TokenTop("Tokens"))
        /*tokenList.add(Token("ETH","200.123","$333,576.00", R.mipmap.eth_icon))
        tokenList.add(Token("BTC","1.123","$2,576.00", R.mipmap.btc_icon))
        tokenList.add(Token("BNB","0.128","$168.02", R.mipmap.eth_icon))
        tokenList.add(Token("USDT","0.128","$168.02", R.mipmap.eth_icon))
        tokenList.add(Token("WLD","0.128","$168.02", R.mipmap.wld_icon))
        tokenList.add(Token("WETH","0.128","$168.02", R.mipmap.eth_icon))*/

        tokenListAdapter = RecyclerViewAdapter(requireContext(),tokenList)
        binding.tokenListView.layoutManager = LinearLayoutManager(context)
        binding.tokenListView.adapter = tokenListAdapter

        initAddressInfo()
        binding.copyIcon.setOnClickListener {
            CommonUtil.clipboardText(requireContext(),binding.addressTv.text.toString())
            Toast.makeText(requireContext(),resources.getString(R.string.copied_hint),Toast.LENGTH_SHORT).show()
        }
    }

    private var isLoadDataing = false
    private var loadDataJob: Job? = null
    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun initAddressInfo() {
        if (isLoadDataing){
            Log.d(GlobalConstant.APP_TAG, "isLoadDataing = $isLoadDataing")
            return
        }
        loadDataJob?.cancel()
        loadDataJob = lifecycleScope.launch {
            isLoadDataing = true
            tokenCount = 0.0
            tokenList.clear()
            tokenList.add(TokenTop("Tokens"))

            var address : String? = null
            withContext(Dispatchers.IO) {
                address = mpc.getMyAddress(IMpc.ROLE_CLIENT, FileUitl.getConfigDir())
                var info : WalletMessage.GetAssetResult? = null
                // 主网数据
                address?.let {
                    info = mpc.getMyAssets(it,false)
                }
                dealDetail(false,info)

                // test
                address?.let {
                    info = mpc.getMyAssets(it,true)
                }
                dealDetail(true,info)

            }
            withContext(Dispatchers.Main) {
                binding.refreshLayout.isRefreshing = false
                binding.addressTv.text = address+""
                tokenListAdapter.notifyDataSetChanged()
                binding.amountTv.text = CommonUtil.formattedValue(tokenCount)
            }
            isLoadDataing = false
        }
    }

    private fun dealDetail(testNet : Boolean,info: WalletMessage.GetAssetResult?) {
        info?.let {
            it.myAsset?.assetsList?.forEach { asset ->
                when (asset.type) {
                    WalletMessage.AssetType.ETH -> {
                        tokenCount += asset.amount.toDouble()
                        if (!testNet){
                            tokenList.add(Token("ETH", asset.amount, asset.amount, R.mipmap.eth_icon,testNet))
                        }else{
                            tokenList.add(Token("Sepolia ETH", asset.amount, asset.amount, R.mipmap.eth_icon,testNet))
                        }
                    }

                    WalletMessage.AssetType.BITCOINT -> {
                        tokenList.add(Token("BTC", asset.amount, asset.amount, R.mipmap.btc_icon))
                    }

                    WalletMessage.AssetType.UNRECOGNIZED -> {
                        tokenList.add(Token("BTC", asset.amount, asset.amount, R.mipmap.btc_icon))
                    }

                    else -> {
                        tokenList.add(Token("BTC", asset.amount, asset.amount, R.mipmap.btc_icon))
                    }
                }
            }
        }
    }

    fun toReceive(){
        /*val intent = Intent(requireContext(), MainActivity::class.java)
        startActivity(intent)*/
        Toast.makeText(requireContext(),getString(R.string.wait_upgrade),Toast.LENGTH_SHORT).show()
    }

    fun toTrans(){
        val intent = Intent(requireContext(), TransInfoActivity::class.java)
        intent.putExtra(TOKEN_INFO,tokenList)
        startActivity(intent)
    }
}