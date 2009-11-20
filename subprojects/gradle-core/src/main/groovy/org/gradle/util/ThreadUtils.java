/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

/**
 * @author Tom Eyckmans
 */
public class ThreadUtils {

    public static int threadPoolSize(int minimalSize) {
        int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
        if (threadPoolSize < minimalSize) {
            threadPoolSize = minimalSize;
        }
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
        while (!joined) {
            try {
                threadToJoinWith.join();
                joined = true;
            } catch (InterruptedException e) {
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

    public static <T extends ExecutorService> void awaitTermination(T executorService,
                                                                    InterruptHandler<T> interruptHandler) {
        boolean stopped = false;
        while (!stopped) {
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                stopped = true;
            } catch (InterruptedException e) {
                stopped = interruptHandler.handleIterrupt(executorService, e);
            }
        }
    }

    public static Thread run(Runnable runnable) {
        final Thread runnableThread = new Thread(runnable);

        runnableThread.start();

        return runnableThread;
    }

    public static void interleavedConditionWait(Lock lock, Condition condition, long waitLength, TimeUnit waitUnit,
                                                ConditionWaitHandle handle) {
        while (!handle.checkCondition()) {
            lock.lock();
            try {
                condition.await(waitLength, waitUnit);

                Thread.yield();
            } catch (InterruptedException e) {
                // ignore - TODO add interruptHandler?
            } finally {
                lock.unlock();
            }
        }

        handle.conditionMatched();
    }
}
