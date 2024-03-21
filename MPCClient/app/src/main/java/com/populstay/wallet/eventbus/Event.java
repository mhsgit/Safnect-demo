package com.populstay.wallet.eventbus;

public class Event {

    public int type;
    public Object obj;

    public Event(int type) {
        this.type = type;
    }

    public Event(int type, Object obj) {
        this.type = type;
        this.obj = obj;
    }

    public interface EventType {
        // 返回钱包首页
        int BACK_WALLET = 1;
        // 返回转账记录页面
        int BACK_TRANSFER = 2;
        int BACK_TRANSFER_SUB = 3;
    }
}
