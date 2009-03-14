package org.gradle.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Eyckmans
 */
public class ThreadUtils {
    
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

    public static <T extends ExecutorService> void shutdown(T executorService, InterruptHandler<T> interuptHandler) {
        executorService.shutdown();

        boolean stopped = false;
        while ( !stopped ) {
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                stopped = true;
            }
            catch (InterruptedException e) {
                stopped = interuptHandler.handleIterrupt(executorService, e);
            }
        }
    }

}
