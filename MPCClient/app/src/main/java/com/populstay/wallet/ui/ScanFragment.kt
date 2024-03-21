package com.populstay.wallet.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import com.populstay.wallet.GlobalConstant
import com.populstay.wallet.R
import com.populstay.wallet.State.BleDeviceState
import com.populstay.wallet.adapter.BleDeviceAdapter
import com.populstay.wallet.databinding.FragmentScanBinding
import com.populstay.wallet.intent.ClientIntent
import com.populstay.wallet.intent.ScanIntent
import com.populstay.wallet.repository.BlueToothBLEUtil
import com.populstay.wallet.vm.ClientViewModel
import com.populstay.wallet.vm.ConnectViewModel
import com.populstay.wallet.vm.ScanViewModel


class ScanFragment : BaseFragment<FragmentScanBinding>() {

    companion object {
        const val TAG = "ScanFragment-->"
        fun newInstance() = ScanFragment()
       const val REQUEST_ENABLE_BT = 1
    }

    private lateinit var mAdapter: BleDeviceAdapter
    private lateinit var scanViewModel: ScanViewModel
    private lateinit var clientViewModel: ClientViewModel
    private lateinit var connectViewModel: ConnectViewModel
    private var times = 0

    private val scanListener = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            try {
                result?.let {
                    if(!BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@let
                    //发送数据
                    lifecycleScope.launch {
                        scanViewModel.actIntent.send(
                            ScanIntent.BleDevice(
                                it.device,
                                it.rssi, it.scanRecord!!.bytes, it.isConnectable, it.scanRecord
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }


    //region ViewBinding定义，防止内存泄露
    override val bindingInflater: (LayoutInflater, ViewGroup?, Bundle?) -> FragmentScanBinding
        get() = { layoutinfalter, viewGroup, _ ->
            FragmentScanBinding.inflate(layoutinfalter, viewGroup, false)
        }

    //endregion


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        scanViewModel = ViewModelProvider(this).get(ScanViewModel::class.java)
        clientViewModel = ViewModelProvider(requireActivity()).get(ClientViewModel::class.java)
        connectViewModel = ViewModelProvider(this).get(ConnectViewModel::class.java)
        mAdapter = BleDeviceAdapter()
        mAdapter.setEmptyViewLayout(requireContext(), R.layout.rcl_bledevice)
        mAdapter.submitList(null)
        mAdapter.addOnItemChildClickListener(R.id.rclconnecBtn) { adapter, view, position ->
            lifecycleScope.launch {
                //连接时要先关闭扫描
                BlueToothBLEUtil.stopScanBlueToothDevice(scanListener)
                val dev = adapter.getItem(position)
                dev?.let {
                    clientViewModel.clientIntent.send(ClientIntent.Connect(it))
                }
            }
        }

        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = mAdapter

        observeViewModel()

        binding.btnbluetoothscan.setOnClickListener {
            checkBT()
        }
    }

    private fun scan(){
        lifecycleScope.launch {0
            scanViewModel.actIntent.send(ScanIntent.InitBleDevices)
            BlueToothBLEUtil.scanBlueToothDevice(scanListener)
        }
    }

    private fun checkBT(){
        if (!BlueToothBLEUtil.isEnabled()) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
            builder.setTitle("提示")
            builder.setMessage("蓝牙未打开，是否打开蓝牙？")
            builder.setPositiveButton(
                "是",
                DialogInterface.OnClickListener { dialog, which -> // 打开蓝牙
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)

                })
            builder.setNegativeButton("否", DialogInterface.OnClickListener { dialog, which ->
                // 用户选择不打开蓝牙
            })
            builder.show()
        }else{
            scan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                // 用户已经成功打开蓝牙
                scan()
            } else {
                // 用户未能成功打开蓝牙
                Toast.makeText(activity, "您拒绝了打开蓝牙", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    scanViewModel.bleDeviceState.collect {
                        Log.d(GlobalConstant.APP_TAG, "$TAG$it")
                        showScanning(false)
                        when (it) {
                            is BleDeviceState.BleDevices -> {
                                mAdapter.submitList(it.devices)
                            }

                            is BleDeviceState.Loading -> {
                                mAdapter.submitList(null)
                                showScanning()
                            }

                            is BleDeviceState.Error -> {
                               /* Toast.makeText(requireContext(), it.error, Toast.LENGTH_SHORT)
                                    .show()*/
                            }

                            else -> {
                              /*  Toast.makeText(requireContext(), "nothing", Toast.LENGTH_SHORT)
                                    .show()*/
                            }
                        }
                    }
                }
            }
        }
    }

    fun showScanning(isShow : Boolean = true){
        binding.scanningText.visibility = if (isShow) View.VISIBLE else View.GONE
    }
}
