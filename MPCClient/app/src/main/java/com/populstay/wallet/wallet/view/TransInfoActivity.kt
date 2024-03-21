package com.populstay.wallet.wallet.view

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import com.populstay.wallet.CommonUtil
import com.populstay.wallet.DecimalDigitsInputFilter
import com.populstay.wallet.R
import com.populstay.wallet.base.BaseActivity
import com.populstay.wallet.databinding.ActivityTransInfoBinding
import com.populstay.wallet.home.model.bean.Token
import com.populstay.wallet.home.model.bean.TokenVH
import com.populstay.wallet.home.view.WalletFragment
import com.populstay.wallet.wallet.model.bean.NetWorkBean
import com.populstay.wallet.wallet.model.bean.SelectTransInfo
import com.populstay.wallet.wallet.model.bean.TransInfo
import com.populstay.wallet.wallet.view.adapter.TransInfoAdapter

class TransInfoActivity : BaseActivity() {

    companion object{
        const val TRANS_INFO = "trans_info"
    }

    private lateinit var binding: ActivityTransInfoBinding

    private  val mInitData by lazy {
        TransInfo(crypto= mutableListOf("Tester’s Wallet"),
            account= mutableListOf(Token("ETH","1","1", R.mipmap.eth_icon)),
            amount = 0.0,
            available = 0.0,
            availableTest = 0.0,
            fee = 0.0,
            unit = "ETH",
            address = "",
            network = mutableListOf(
                NetWorkBean(NetWorkBean.NET_TYPE_MAIN_NAME,NetWorkBean.NET_TYPE_MAIN),
                NetWorkBean(NetWorkBean.NET_TYPE_TEST_NAME,NetWorkBean.NET_TYPE_TEST))
        )
    }
    private lateinit var mSelectData : SelectTransInfo



    private val mCryptoAdapter by lazy {
        TransInfoAdapter(this@TransInfoActivity,mInitData.crypto)
    }
    private val mNetworkAdapter by lazy {
        TransInfoAdapter(this@TransInfoActivity,mInitData.network)
    }
    private val mAccountAdapter by lazy {
        TransInfoAdapter(this@TransInfoActivity,mInitData.account)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getInitData()
        initTitleBar()
        initView()
    }


    override fun initTitleBar() {
        binding.commonTitleLayout.titleTv.visibility = View.GONE
        binding.commonTitleLayout.descTv.visibility = View.GONE
        binding.commonTitleLayout.titleBar.setOnClickListener {
            finish()
        }
    }

    private fun initView() {
        initDeviceList()
        if (mSelectData.network?.netType != 1){
            binding.availableAmountTv.text = "${CommonUtil.formattedValue(mSelectData.availableTest)} ${mInitData.unit}"
        }else{
            binding.availableAmountTv.text = "${CommonUtil.formattedValue(mSelectData.available)} ${mInitData.unit}"
        }

        binding.amountMaxBtn.setOnClickListener {
            if (mSelectData.network?.netType != 1){
                binding.amountEt.setText("${CommonUtil.formattedValue(mInitData.availableTest)}")
            }else{
                binding.amountEt.setText("${CommonUtil.formattedValue(mInitData.available)}")
            }
            binding.amountEt.setSelection(binding.amountEt.text.toString().length)
        }

        if (mSelectData.network?.netType != 1){
            binding.amountEt.setText("${CommonUtil.formattedValue(mInitData.availableTest)}")
        }else{
            binding.amountEt.setText("${CommonUtil.formattedValue(mInitData.available)}")
        }
        binding.amountEt.setSelection(binding.amountEt.text.toString().length)
        binding.addressEt.setText("")

        binding.amountEt.filters = arrayOf(DecimalDigitsInputFilter(6))

        binding.amountEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                updateSelectData()
            }
        })
        binding.addressEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                updateSelectData()
            }
        })
    }

    private fun updateSelectData(){

        mSelectData.address = binding.addressEt.text.toString()
        val amountText = binding.amountEt.text.toString()
        mSelectData.amount = if (amountText.isEmpty()) 0.0 else amountText.toDouble()
        binding.continueBtn.isEnabled = !(mSelectData.address.isEmpty() || mSelectData.amount <= 0.0)
    }

    private fun initDeviceList() {
        binding.cryptoAssetSpinner.adapter = mCryptoAdapter
        binding.cryptoAssetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = parent?.getItemAtPosition(position)
                mSelectData.crypto = selectedItem as String
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 未选择任何项时的处理
            }
        }

        binding.networkSpinner.adapter = mNetworkAdapter
        binding.networkSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val selectedItem = parent?.getItemAtPosition(position)
                mSelectData.network = selectedItem as NetWorkBean

                if (mSelectData.network?.netType != 1){
                    binding.availableAmountTv.text = "${CommonUtil.formattedValue(mSelectData.availableTest)} ${mInitData.unit}"
                }else{
                    binding.availableAmountTv.text = "${CommonUtil.formattedValue(mSelectData.available)} ${mInitData.unit}"
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 未选择任何项时的处理
            }
        }

        binding.accountSpinner.adapter = mAccountAdapter
        binding.accountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val selectedItem = parent?.getItemAtPosition(position)
                mSelectData.account = (selectedItem as Token).label
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 未选择任何项时的处理
            }
        }

        binding.continueBtn.setOnClickListener {
            toTransActivity()
            finish()
        }
    }

    private fun toTransActivity(){
        val intent = Intent(this@TransInfoActivity, TransActivity::class.java)
        intent.putExtra(TRANS_INFO,mSelectData)
        startActivity(intent)
    }

    private fun getInitData(){

        try {
            val tokenList: ArrayList<TokenVH> = intent.getSerializableExtra(WalletFragment.TOKEN_INFO) as ArrayList<TokenVH>
            mInitData.account.clear()
            tokenList.forEach {
                if (it is Token){
                    mInitData.account.add(it)
                    if (it.testNet){
                        mInitData.availableTest = it.amount.toDouble()
                    }else{
                        mInitData.available = it.amount.toDouble()
                    }
                }
            }

            mSelectData = SelectTransInfo()
            mSelectData.crypto = mInitData.crypto[0]
            mSelectData.account = mInitData.account[0].label
            mSelectData.available = mInitData.available
            mSelectData.fee = mInitData.fee
            mSelectData.availableTest = mInitData.availableTest
            mSelectData.network = NetWorkBean(NetWorkBean.NET_TYPE_MAIN_NAME,NetWorkBean.NET_TYPE_MAIN)
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

}