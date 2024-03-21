package vac.test.bluetoothbledemo.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadManager{
    private ThreadManager(){
        mExecutorService = new ThreadPoolExecutor(1,1,0, TimeUnit.SECONDS,new PriorityBlockingQueue<Runnable>());
    }

    private static class SingleTonHolder {
        private static ThreadManager INSTANCE = new ThreadManager();
    }

    public static ThreadManager getInstance(){
        return SingleTonHolder.INSTANCE;
    }

    private ExecutorService mExecutorService;


    public void execute(PriorityRunnable runnable){
        mExecutorService.execute(runnable);
    }

}
