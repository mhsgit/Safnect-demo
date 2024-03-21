package com.populstay.wallet.transaction.view.adapter;


import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.populstay.wallet.CommonUtil;
import com.populstay.wallet.R;
import com.populstay.wallet.transaction.model.bean.TransRecord;
import com.populstay.wallet.transaction.view.WebViewActivity;

import java.util.List;

public class TransRecordListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {


    private List<TransRecord> mDataList;


    //上下文
    private Context context;

    public static final int TYPE_ITEM = 1;
    public static final int TYPE_NO_DATA = 2;

    /**
     * 构造方法
     *
     * @param context
     */
    public TransRecordListAdapter(Context context, List<TransRecord> dataList) {
        this.context = context;
        this.mDataList = dataList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        //这时候就要根据这个i来判断加哪一个布局了
        if (viewType == TYPE_ITEM) {
            View inflate = LayoutInflater.from(context).inflate(R.layout.trans_record_list_item, viewGroup, false);
            return new TwoItemHolder(inflate);
        } else if (viewType == TYPE_NO_DATA) {
            View inflate = LayoutInflater.from(context).inflate(R.layout.no_data_view, viewGroup, false);
            return  new NoDataItemHolder(inflate);
        }
        return null;
    }

    /**
     * 绑定控件，这里可以写一些事件方法等
     *
     * @param viewHolder
     * @param i
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        TransRecord item = mDataList.get(i);
       if (viewHolder instanceof TwoItemHolder) {
           TwoItemHolder twoItemHolder = (TwoItemHolder) viewHolder;
           twoItemHolder.token_type_tv.setText(item.getTestNet() ? "Sepolia " + item.getToken_type() : item.getToken_type());
           twoItemHolder.time_tv.setText(item.getTime());
           twoItemHolder.trans_type_tv.setText(item.getTrans_type());
           if (item.getTrans_type_code() == TransRecord.TRANS_TYPE_CODE_TRANSFER){
               twoItemHolder.trans_type_tv.setTextColor(context.getResources().getColor(R.color.color_ff0b0bd8));
               twoItemHolder.fee_tv.setVisibility(View.VISIBLE);
               twoItemHolder.total_tv.setVisibility(View.VISIBLE);
               twoItemHolder.fee_val_tv.setVisibility(View.VISIBLE);
               twoItemHolder.total_val_tv.setVisibility(View.VISIBLE);
               twoItemHolder.fee_val_tv.setText(item.getFee_val());
               twoItemHolder.total_val_tv.setText(item.getTotal_val());
               twoItemHolder.receiver_tv.setText("Receiver");
           }else {
               twoItemHolder.trans_type_tv.setTextColor(context.getResources().getColor(R.color.color_ffd8550b));
               twoItemHolder.fee_tv.setVisibility(View.GONE);
               twoItemHolder.total_tv.setVisibility(View.GONE);
               twoItemHolder.fee_val_tv.setVisibility(View.GONE);
               twoItemHolder.total_val_tv.setVisibility(View.GONE);
               twoItemHolder.receiver_tv.setText("Sender");
           }
           twoItemHolder.sent_hash_val_tv.setText(item.getSent_hash());
           //twoItemHolder.testIcon.setVisibility(item.getTestNet() ? View.VISIBLE : View.GONE);

           twoItemHolder.sent_hash_val_tv.setTag(item);
           twoItemHolder.sent_hash_val_tv.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View v) {

                   TransRecord record = (TransRecord) v.getTag();
                   Intent intent = new Intent(context, WebViewActivity.class);
                   intent.putExtra(WebViewActivity.TEST,record.getTestNet());
                   intent.putExtra(WebViewActivity.URL,record.getSent_hash());
                   context.startActivity(intent);
                   //CommonUtil.INSTANCE.clipboardText(context,((TextView)v).getText().toString());
                   //Toast.makeText(context,context.getResources().getString(R.string.copied_hash_hint), Toast.LENGTH_SHORT).show();
               }
           });

           twoItemHolder.trans_status_tv.setText(item.getTrans_status());
           if (item.getTrans_status_code() == TransRecord.TRANS_STATUS_CODE_SUCCESS){
               twoItemHolder.trans_status_tv.setBackgroundResource(R.drawable.transfer_record_status_success);
               twoItemHolder.trans_status_tv.setTextColor(context.getResources().getColor(R.color.color_ff198bff));
           }
           else if (item.getTrans_status_code() == TransRecord.TRANS_STATUS_CODE_PENDING){
               twoItemHolder.trans_status_tv.setBackgroundResource(R.drawable.transfer_record_status_pending);
               twoItemHolder.trans_status_tv.setTextColor(context.getResources().getColor(R.color.color_ffa500));
           }
           else {
               twoItemHolder.trans_status_tv.setBackgroundResource(R.drawable.transfer_record_status_failed);
               twoItemHolder.trans_status_tv.setTextColor(context.getResources().getColor(R.color.color_ffff4050));
           }
           twoItemHolder.receiver_address_tv.setText(item.getReceiver_address());
           twoItemHolder.amount_val_tv.setText(item.getAmount_val());
        }else if (viewHolder instanceof NoDataItemHolder){
           // 无数据
       }
    }

    /**
     * 返回条目总数量，假设16个条目
     *
     * @return
     */
    @Override
    public int getItemCount() {
        return mDataList.size();
    }

    /**
     * 返回条目类型(这里就做个简单的判断区分)
     *
     * @param position 代表第几个条目
     * @return
     */
    @Override
    public int getItemViewType(int position) {

       return mDataList.get(position).getItem_type();
    }

    class TwoItemHolder extends RecyclerView.ViewHolder {
        TextView token_type_tv;
        TextView time_tv;
        TextView trans_type_tv;
        TextView trans_status_tv;
        TextView receiver_address_tv;
        TextView amount_val_tv;
        TextView fee_val_tv;
        TextView total_val_tv;
        TextView fee_tv;
        TextView total_tv;
        TextView receiver_tv;
        TextView sent_hash_val_tv;
        ImageView testIcon;

        public TwoItemHolder(@NonNull View itemView) {
            super(itemView);
            token_type_tv = itemView.findViewById(R.id.token_type_tv);
            time_tv = itemView.findViewById(R.id.time_tv);
            trans_type_tv = itemView.findViewById(R.id.trans_type_tv);
            trans_status_tv = itemView.findViewById(R.id.trans_status_tv);
            receiver_address_tv = itemView.findViewById(R.id.receiver_address_tv);
            amount_val_tv = itemView.findViewById(R.id.amount_val_tv);
            fee_val_tv = itemView.findViewById(R.id.fee_val_tv);
            total_val_tv = itemView.findViewById(R.id.total_val_tv);
            fee_tv = itemView.findViewById(R.id.fee_tv);
            total_tv = itemView.findViewById(R.id.total_tv);
            receiver_tv = itemView.findViewById(R.id.receiver_tv);
            sent_hash_val_tv = itemView.findViewById(R.id.sent_hash_val_tv);
            testIcon = itemView.findViewById(R.id.testIcon);
        }
    }

    class NoDataItemHolder extends RecyclerView.ViewHolder {
        public NoDataItemHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

}


