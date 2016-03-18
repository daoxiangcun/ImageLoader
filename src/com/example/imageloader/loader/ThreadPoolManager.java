package com.example.imageloader.loader;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolManager {
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1;

    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;


    private ThreadPoolExecutor mThreadPool;

    public void init() {
        mThreadPool = new ThreadPoolExecutor(NUMBER_OF_CORES,    // Initial pool size
                                    NUMBER_OF_CORES,    // max pool size
                                    KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, workQueue)
    }
}
