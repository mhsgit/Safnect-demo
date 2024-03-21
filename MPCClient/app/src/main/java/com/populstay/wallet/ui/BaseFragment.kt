package com.populstay.wallet.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.populstay.wallet.GlobalConstant

abstract class BaseFragment<T : ViewBinding> : Fragment() {

    private var _binding: T? = null
    protected val binding: T get() = _binding!!

    abstract val bindingInflater: (LayoutInflater, ViewGroup?, Bundle?) -> T

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = bindingInflater.invoke(inflater, container, savedInstanceState)
        Log.d(GlobalConstant.APP_TAG, "BaseFragment-->currentFragment=${this::class.simpleName}")
        initView()
        return binding.root
    }

     open fun initView(){

     }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

