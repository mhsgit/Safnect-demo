package vac.test.bluetoothbledemo.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
import vac.test.bluetoothbledemo.GlobalConstant
import vac.test.bluetoothbledemo.R
import vac.test.bluetoothbledemo.State.PhoneState
import vac.test.bluetoothbledemo.databinding.ActivityMainBinding
import vac.test.bluetoothbledemo.intent.ServerIntent
import vac.test.bluetoothbledemo.log.PeachLogger
import vac.test.bluetoothbledemo.repository.BlueToothBLEUtil
import vac.test.bluetoothbledemo.vm.ServerViewModel


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var serverViewModel: ServerViewModel

    private lateinit var serverFragment: ServerFragment
    private lateinit var clientFragment: ClientFragment
    private lateinit var fragments: ArrayList<ServerFragment>
    private lateinit var viewpagerAdapter: FragmentStateAdapter

    companion object{
        const val REQUEST_ENABLE_BT = 1
        const val TAG = "MainActivity"
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestPermission() {
       startGetPermissions(this)
    }

    private fun startGetPermissions(activity : Activity) {
        if (EasyPermissions.hasPermissions(activity, *(if (BlueToothBLEUtil.isAndroid12()) BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS_12 else BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS))) {
            if (!BlueToothBLEUtil.isEnabled()) {
                val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
                builder.setTitle(R.string.hint)
                builder.setMessage(R.string.bluetooth_turned_on_hint)
                builder.setPositiveButton(
                    R.string.yes,
                    DialogInterface.OnClickListener { _, _ ->
                        if(!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@OnClickListener

                        // 打开手机蓝牙开关
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                    })
                builder.setNegativeButton(R.string.no, DialogInterface.OnClickListener { _, _ ->
                    // 用户选择不打开蓝牙
                })
                builder.show()
            }else{
              // ok
                initView()

            }
        } else {
            // 如果没有上述权限 , 那么申请权限
            EasyPermissions.requestPermissions(
                activity,
                activity.resources.getString(R.string.rationale_hint),
                BlueToothBLEUtil.REQUEST_CODE_PERMISSIONS,
                *(if (BlueToothBLEUtil.isAndroid12()) BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS_12 else BlueToothBLEUtil.REQUIRED_BLEPERMISSIONS)
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
               // OK
                requestPermission()

            } else {
                // 用户未能成功打开蓝牙
                Toast.makeText(this@MainActivity, resources.getString(R.string.refused_turn_on_bluetooth), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (BlueToothBLEUtil.REQUEST_CODE_PERMISSIONS == requestCode) {
            for (x in grantResults) {
                if (x == PackageManager.PERMISSION_DENIED) {
                    //权限拒绝了
                    return
                }
            }
            // ok
            requestPermission()

        }
    }

    private val tabTitles by lazy {
        listOf("外围设备(Server)")
    }

    private fun initTabAndViewPager() {
        serverFragment = ServerFragment.newInstance()
        clientFragment = ClientFragment.newInstance()
        fragments = arrayListOf(serverFragment)

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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarTextColor()
        requestPermission()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setStatusBarTextColor()
    }

    private fun setStatusBarTextColor(){
        //设置字体白色
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun initView(){
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
                                binding.tvDeviceName.text = "当前设备:${it.device.manufacturer} ${it.device.modelname}      系统版本:Android ${it.device.version}"
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG observeViewModel 当前设备:${it.device.manufacturer} ${it.device.modelname}      系统版本:Android ${it.device.version}")
                            }

                            is PhoneState.Error -> {
                                binding.tvDeviceName.text = it.error
                                PeachLogger.d(GlobalConstant.APP_TAG, "$TAG observeViewModel Error = ${it.error}")
                            }

                            else -> {

                            }

                        }

                    }
                }
            }
        }
    }
}