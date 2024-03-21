package com.populstay.wallet.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import com.populstay.wallet.State.PhoneState
import com.populstay.wallet.databinding.ActivityMainBinding
import com.populstay.wallet.intent.ServerIntent
import com.populstay.wallet.repository.BlueToothBLEUtil
import com.populstay.wallet.vm.ConnectViewModel
import com.populstay.wallet.vm.ServerViewModel


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var serverViewModel: ServerViewModel
    private lateinit var connectViewModel: ConnectViewModel

    private lateinit var serverFragment: ServerFragment
    private lateinit var clientFragment: ClientFragment
    private lateinit var fragments: ArrayList<BaseFragment<out ViewBinding>>
    private lateinit var viewpagerAdapter: FragmentStateAdapter

    @AfterPermissionGranted(BlueToothBLEUtil.REQUEST_CODE_PERMISSIONS)
    fun requestPermission() {
        //kotlin传入可变参数数组需要前面加上*
        if (EasyPermissions.hasPermissions(this, *BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS)) {
            Toast.makeText(this, "权限申请通过", Toast.LENGTH_LONG).show()
        } else {
            // 如果没有上述权限 , 那么申请权限
            EasyPermissions.requestPermissions(
                this,
                "需要申请蓝牙权限",
                BlueToothBLEUtil.REQUEST_CODE_PERMISSIONS,
                *BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS
            )

        }
    }

//    private val fragments by lazy {
//        listOf(ScanFragment(), ServerFragment())
//    }

    private val tabTitles by lazy {
        listOf("中心设备(Client)")
    }

    private fun initTabAndViewPager() {
        serverFragment = ServerFragment.newInstance()
        clientFragment = ClientFragment.newInstance()
        fragments = arrayListOf(clientFragment)

        viewpagerAdapter =  object :
            FragmentStateAdapter(supportFragmentManager, this.lifecycle) {
            override fun getItemCount(): Int {
                return fragments.size
            }

            override fun createFragment(position: Int): Fragment {
                return fragments[position]
            }

        }
        binding.viewPager.adapter = viewpagerAdapter

        val tabLayoutMediator =
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = tabTitles[position]
            }
        tabLayoutMediator.attach()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        //设置字体黑色
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //设置字体黑色
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        requestPermission()

        initTabAndViewPager()

        serverViewModel = ViewModelProvider(this).get(ServerViewModel::class.java)

        binding.tvDeviceName.setOnClickListener {
            lifecycleScope.launch {
                serverViewModel.serverIntent.send(ServerIntent.Info("测试使用"))
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    serverViewModel.phone.collect {
                        when (it) {
                            is PhoneState.Phone -> {
                                binding.tvDeviceName.text =
                                    "当前设备:${it.device.manufacturer} ${it.device.modelname}      系统版本:Android ${it.device.version}"
                            }

                            is PhoneState.Error -> {
                                binding.tvDeviceName.text = it.error
                                Toast.makeText(this@MainActivity, it.error, Toast.LENGTH_SHORT)
                                    .show()
                            }

                            else -> {

                            }

                        }

                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        clientFragment?.onActivityResult(requestCode, resultCode, data)
    }

}