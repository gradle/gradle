/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class FileWatcherTask implements Runnable, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWatcherTask.class);
    static final int POLL_TIMEOUT_MILLIS = 250;
    private static final int STOP_TIMEOUT_SECONDS = 10;
    private final AtomicBoolean runningFlag;
    private final WatchStrategy watchStrategy;
    private final WatchListener listener;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    public FileWatcherTask(WatchStrategy watchStrategy, WatchListener listener) throws IOException {
        this.listener = listener;
        this.runningFlag = new AtomicBoolean(true);
        this.watchStrategy = watchStrategy;
    }

    public void run() {
        try {
            try {
                watchLoop();
            } catch (InterruptedException e) {
                // ignore
            }
        } finally {
            watchStrategy.close();
        }
    }

    protected void watchLoop() throws InterruptedException {
        while (watchLoopRunning()) {
            watchStrategy.pollChanges(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS, listener);
        }
        stopLatch.countDown();
    }

    protected boolean watchLoopRunning() {
        return runningFlag.get() && !Thread.currentThread().isInterrupted();
    }

    @Override
    public synchronized void stop() {
        if (runningFlag.compareAndSet(true, false)) {
            try {
                stopLatch.await(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}
