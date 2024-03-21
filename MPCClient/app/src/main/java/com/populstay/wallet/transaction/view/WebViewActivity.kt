package com.populstay.wallet.transaction.view

import android.os.Bundle
import com.populstay.wallet.base.BaseActivity
import com.populstay.wallet.databinding.ActivityWebViewBinding

class WebViewActivity : BaseActivity() {

    companion object{
        const val URL = "url"
        const val TEST = "test"
    }
    private lateinit var binding: ActivityWebViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(URL)
        val isTest = intent.getBooleanExtra(TEST,false)
        url?.let {
            if (isTest){
                binding.webView.loadUrl("https://sepolia.etherscan.io/tx/$url")
            }else{
                binding.webView.loadUrl("https://etherscan.io/tx/$url")
            }
        }
    }

    override fun initTitleBar() {
    }
}