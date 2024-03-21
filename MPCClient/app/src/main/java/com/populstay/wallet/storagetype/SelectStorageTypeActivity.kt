package com.populstay.wallet.storagetype

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.populstay.wallet.R
import com.populstay.wallet.TestActivity
import com.populstay.wallet.base.BaseActivity
import com.populstay.wallet.databinding.ActivitySelectStorageTypeBinding
import com.populstay.wallet.device.DKGActivity
import com.populstay.wallet.mpc.ImplMpc
import com.populstay.wallet.ui.MainActivity

class SelectStorageTypeActivity: BaseActivity() {

    lateinit var binding:  ActivitySelectStorageTypeBinding
    private val mpc by lazy {
        ImplMpc()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectStorageTypeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.hardwareWallet.setOnClickListener {
            toDeviceListActivity()
            finish()
        }
        binding.shareWallet.setOnClickListener {
           Toast.makeText(this@SelectStorageTypeActivity,getString(R.string.wait_upgrade),Toast.LENGTH_SHORT).show()
        }
    }

    override fun initTitleBar() {
        TODO("Not yet implemented")
    }

    private fun toDeviceListActivity(){
        val intent = Intent(this@SelectStorageTypeActivity, DKGActivity::class.java)
        startActivity(intent)
    }

    private fun toMain(){
        val intent = Intent(this@SelectStorageTypeActivity, MainActivity::class.java)
        startActivity(intent)
    }

    fun toTestActivity(){
        val intent = Intent(this@SelectStorageTypeActivity, TestActivity::class.java)
        startActivity(intent)
    }

}