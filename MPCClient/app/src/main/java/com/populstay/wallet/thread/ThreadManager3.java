package com.populstay.wallet.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadManager3 {
    private ThreadManager3(){
        mExecutorService = new ThreadPoolExecutor(1,1,0, TimeUnit.SECONDS,new PriorityBlockingQueue<Runnable>());
    }

    private static class SingleTonHolder {
        private static ThreadManager3 INSTANCE = new ThreadManager3();
    }

    public static ThreadManager3 getInstance(){
        return SingleTonHolder.INSTANCE;
    }

    private ExecutorService mExecutorService;


    public void execute(PriorityRunnable runnable){
        mExecutorService.execute(runnable);
    }

}
