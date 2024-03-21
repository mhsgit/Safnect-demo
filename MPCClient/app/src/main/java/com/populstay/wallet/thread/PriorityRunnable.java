package com.populstay.wallet.thread;

public abstract class PriorityRunnable implements Runnable,Comparable<PriorityRunnable>{

    private int mPriority = 0;

    public PriorityRunnable() {
       this(0);
    }
    public PriorityRunnable(int mPriority) {
        this.mPriority = mPriority;
    }

    @Override
    public int compareTo(PriorityRunnable o) {
        int myPriority= this.mPriority;
        int oP = o.mPriority;
        return myPriority < oP ? 1  : myPriority > oP ? -1 : 0;
    }

    @Override
    public void run() {
        onRun();
    }

    public abstract void onRun();
}
