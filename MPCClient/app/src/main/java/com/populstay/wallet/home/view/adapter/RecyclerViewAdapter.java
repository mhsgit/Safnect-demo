package com.populstay.wallet.home.view.adapter;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.populstay.wallet.CommonUtil;
import com.populstay.wallet.R;
import com.populstay.wallet.home.model.bean.Token;
import com.populstay.wallet.home.model.bean.TokenTop;
import com.populstay.wallet.home.model.bean.TokenVH;

import java.util.List;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {


    private List<TokenVH> mDataList;


    //上下文
    private Context context;

    /**
     * 构造方法
     *
     * @param context
     */
    public RecyclerViewAdapter(Context context,List<TokenVH> dataList) {
        this.context = context;
        this.mDataList = dataList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        //这时候就要根据这个i来判断加哪一个布局了

        View inflate = null;
        RecyclerView.ViewHolder viewHolder = null;

        //根据i返回不同布局
        switch (i) {
            case TokenVH.ITEM_TYPE_TOKEN_TOP:
                inflate = LayoutInflater.from(context).inflate(R.layout.token_list_item_top, viewGroup, false);
                viewHolder = new OneItemHolder(inflate);
                break;
            case TokenVH.ITEM_TYPE_TOKEN:
                inflate = LayoutInflater.from(context).inflate(R.layout.token_list_item, viewGroup, false);
                viewHolder = new TwoItemHolder(inflate);
                break;
        }

        //返回布局
        return viewHolder;
    }

    /**
     * 绑定控件，这里可以写一些事件方法等
     *
     * @param viewHolder
     * @param i
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        TokenVH item = mDataList.get(i);
        if (viewHolder instanceof OneItemHolder) {
            ((OneItemHolder) viewHolder).label_tv.setText(((TokenTop)item).getLabel());
            //写绑定或这写事件可以如下
           /* ((OneItemHolder) viewHolder).one_text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(context, "Toast就想找个女朋友", Toast.LENGTH_SHORT).show();
                }
            });*/

        } else if (viewHolder instanceof TwoItemHolder) {
            ((TwoItemHolder) viewHolder).label_tv.setText(((Token)item).getLabel());
            ((TwoItemHolder) viewHolder).amount_tv.setText(((Token)item).getAmount());
            ((TwoItemHolder) viewHolder).number_tv.setText(CommonUtil.INSTANCE.formattedValue(Double.parseDouble(((Token)item).getAmount())));
            ((TwoItemHolder) viewHolder).token_type_icon.setImageResource(((Token)item).getIcon());
            //((TwoItemHolder) viewHolder).testIcon.setVisibility(((Token)item).getTestNet() ? View.VISIBLE : View.GONE);
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

       return mDataList.get(position).getItemType();
    }

    class OneItemHolder extends RecyclerView.ViewHolder {

        //举例
        TextView label_tv;

        public OneItemHolder(@NonNull View itemView) {
            super(itemView);
            label_tv = itemView.findViewById(R.id.label_tv);
        }
    }

    class TwoItemHolder extends RecyclerView.ViewHolder {
        ImageView token_type_icon;
        TextView label_tv;
        TextView number_tv;
        TextView amount_tv;
        ImageView testIcon;
        public TwoItemHolder(@NonNull View itemView) {
            super(itemView);
            token_type_icon = itemView.findViewById(R.id.token_type_icon);
            label_tv = itemView.findViewById(R.id.token_type_tv);
            number_tv = itemView.findViewById(R.id.number_tv);
            amount_tv = itemView.findViewById(R.id.amount_tv);
            testIcon = itemView.findViewById(R.id.testIcon);
        }
    }

}


