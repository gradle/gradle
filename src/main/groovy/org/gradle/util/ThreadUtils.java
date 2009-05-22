package org.gradle.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

/**
 * @author Tom Eyckmans
 */
public class ThreadUtils {

    public static int threadPoolSize(int minimalSize) {
        int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
        if ( threadPoolSize < minimalSize ) threadPoolSize = minimalSize;
        return threadPoolSize;
    }

    public static ExecutorService newFixedThreadPool(int minimalSize) {
        return Executors.newFixedThreadPool(threadPoolSize(minimalSize));
    }

    public static <T extends Thread> void join(T threadToJoinWith) {
        join(threadToJoinWith, new IgnoreInterruptHandler<T>());
    }

    public static <T extends Thread> void join(T threadToJoinWith, InterruptHandler<T> interruptHandler) {
        boolean joined = false;
        while ( !joined ) {
            try {
                threadToJoinWith.join();
                joined = true;
            }
            catch ( InterruptedException e ) {
                joined = interruptHandler.handleIterrupt(threadToJoinWith, e);
            }
        }
    }

    public static <T extends ExecutorService> void shutdown(T executorService) {
        shutdown(executorService, new IgnoreInterruptHandler<T>());
    }

    public static <T extends ExecutorService> void shutdown(T executorService, InterruptHandler<T> interruptHandler) {
        executorService.shutdown();

        awaitTermination(executorService, interruptHandler);
    }

    public static <T extends ExecutorService> void awaitTermination(T executorService) {
        awaitTermination(executorService, new IgnoreInterruptHandler<T>());
    }

    public static <T extends ExecutorService> void awaitTermination(T executorService, InterruptHandler<T> interruptHandler)  {
        boolean stopped = false;
        while ( !stopped ) {
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                stopped = true;
            }
            catch (InterruptedException e) {
                stopped = interruptHandler.handleIterrupt(executorService, e);
            }
        }
    }

    public static Thread run(Runnable runnable) {
        final Thread runnableThread = new Thread(runnable);

        runnableThread.start();

        return runnableThread;
    }
}
