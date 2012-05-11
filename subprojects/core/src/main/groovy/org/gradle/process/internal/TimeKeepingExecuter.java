/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * by Szczepan Faber, created at: 4/29/12
 */
public class TimeKeepingExecuter {

    private final static Logger LOGGER = Logging.getLogger(TimeKeepingExecuter.class);
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean completed;
    private String displayName;

    /**
     * Executes 'operation'. Executes 'onTimeout' Runnable should the timeout occur (i.e. 'operation' didn't complete on time).
     *
     * @param operation to execute
     * @param onTimeout operation to execute should the timeout occur (or interruption)
     * @param timeoutMillis timeout value
     */
    void execute(Runnable operation, Runnable onTimeout, int timeoutMillis, String displayName) {
        this.displayName = displayName;
        StoppableExecutor executor = new DefaultExecutorFactory().create(displayName);
        executor.execute(withDetectingCompletion(operation));
        executor.execute(withTimeout(onTimeout, timeoutMillis));
        executor.stop();
    }

    private Runnable withTimeout(final Runnable onTimeout, final int timeoutMillis) {
        return new Runnable() {
            public void run() {
                try {
                    lock.lock();
                    if (completed) {
                        //already done? excellent!
                        return;
                    }
                    condition.await(timeoutMillis, TimeUnit.MILLISECONDS);
                    if (!completed) {
                        LOGGER.debug("Timeout ({} millis) hit while running {}.", timeoutMillis, displayName);
                        onTimeout.run();
                    }
                } catch (InterruptedException e) {
                    //ok, let's continue
                } finally {
                    lock.unlock();
                }
            }
        };
    }

    private Runnable withDetectingCompletion(final Runnable operation) {
        return new Runnable() {
            public void run() {
                LOGGER.debug("Running: " + displayName);
                try {
                    operation.run();
                } finally {
                    lock.lock();
                    try {
                        completed = true;
                        condition.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        };
    }
}
