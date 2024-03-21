package com.populstay.wallet.home.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.populstay.wallet.BuildConfig
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.databinding.FragmentMeBinding
import com.populstay.wallet.ui.BaseFragment


class MeFragment : BaseFragment<FragmentMeBinding>() {


    companion object {
        fun newInstance() = MeFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override val bindingInflater: (LayoutInflater, ViewGroup?, Bundle?) -> FragmentMeBinding
        get() = { layoutInflater, viewGroup, _ ->
            FragmentMeBinding.inflate(layoutInflater, viewGroup, false)
    }

    override fun initView() {
        super.initView()
        binding.versionTv.text = "Version:${BuildConfig.VERSION_NAME}"
        binding.netTypeTv.text = "NetWork:${if (GlobalConstant.TEST_NET) "TestNet" else "MainNet"}"

    }

}