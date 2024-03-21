package com.populstay.wallet.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadManager2 {
    private ThreadManager2(){
        mExecutorService = new ThreadPoolExecutor(1,1,0, TimeUnit.SECONDS,new PriorityBlockingQueue<Runnable>());
    }

    private static class SingleTonHolder {
        private static ThreadManager2 INSTANCE = new ThreadManager2();
    }

    public static ThreadManager2 getInstance(){
        return SingleTonHolder.INSTANCE;
    }

    private ExecutorService mExecutorService;


    public void execute(PriorityRunnable runnable){
        mExecutorService.execute(runnable);
    }

}
