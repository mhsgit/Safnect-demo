package com.populstay.wallet.wallet.view.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.populstay.wallet.R
import com.populstay.wallet.home.model.bean.Token
import com.populstay.wallet.wallet.model.bean.NetWorkBean
import kotlinx.android.synthetic.main.device_spinner_item_layout.view.icon
import kotlinx.android.synthetic.main.device_spinner_item_layout.view.name


class TransInfoAdapter(val mContext: Context, val mDataList: List<Any>) : BaseAdapter() {

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

        val curItem = mDataList[position]
        if (curItem is String){
            itemView.name.text = curItem
            itemView.icon.visibility = View.GONE
        }else if (curItem is Token){
            itemView.icon.visibility = View.VISIBLE
            itemView.name.text = curItem.label
            itemView.icon.setImageResource(curItem.icon)
        } else if (curItem is NetWorkBean){
            itemView.name.text = curItem.netName
            itemView.icon.visibility = View.GONE
        }
        return itemView
    }
}


