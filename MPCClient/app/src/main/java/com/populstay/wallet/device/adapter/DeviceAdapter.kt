package com.populstay.wallet.device.adapter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.app.ActivityCompat
import com.populstay.wallet.R
import com.populstay.wallet.bean.CBleDevice
import com.populstay.wallet.device.BTBean
import com.populstay.wallet.repository.BlueToothBLEUtil
import kotlinx.android.synthetic.main.device_spinner_item_layout.view.name


class DeviceAdapter(val mContext: Context,val mDataList: List<BTBean>) : BaseAdapter() {

    override fun getCount(): Int {
        return mDataList.size
    }

    override fun getItem(position: Int): Any {
        return mDataList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val itemView = LayoutInflater.from(mContext).inflate(R.layout.device_spinner_item_layout,null)
        if (BTBean.TYPE_SCAN == mDataList[position].type){
            itemView.name.text = mContext.getString(R.string.searching)
            itemView.name.setTextColor(mContext.resources.getColor(R.color.color_ff919599))
        }else {
            if(BlueToothBLEUtil.checkBlueToothPermission(Manifest.permission.BLUETOOTH_CONNECT)){
                itemView.name.text = mDataList[position].name ?: "N/A"
                itemView.name.setTextColor(mContext.resources.getColor(R.color.color_ff1f2021))
            }
        }
        return itemView
    }
}


