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

import org.gradle.internal.filewatch.FileWatchInputs;
import org.gradle.internal.filewatch.FileWatcher;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation for {@link FileWatcher}
 */
public class DefaultFileWatcher implements FileWatcher {
    private final ExecutorService executor;
    private AtomicBoolean runningFlag = new AtomicBoolean(false);
    private Future<?> execution;
    private static final int STOP_TIMEOUT_SECONDS = 10;

    public DefaultFileWatcher(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public synchronized void watch(FileWatchInputs inputs, Runnable callback) {
        if(!runningFlag.compareAndSet(false, true)) {
            throw new IllegalStateException("FileWatcher cannot start watching new inputs when it's already running.");
        }
        submitWithLatch(inputs, callback);
    }

    private void submitWithLatch(FileWatchInputs inputs, Runnable callback) {
        CountDownLatch latch = createLatch();
        execution = executor.submit(new FileWatcherExecutor(this, runningFlag, callback, new ArrayList(inputs.getDirectoryTrees()), new ArrayList(inputs.getFiles()), latch));
        try {
            // wait until watching is active
            latch.await();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    protected CountDownLatch createLatch() {
        return new CountDownLatch(1);
    }

    @Override
    public synchronized void stop() {
        if(runningFlag.get()) {
            runningFlag.set(false);
            waitForStop();
        }
    }

    private void waitForStop() {
        try {
            execution.get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        } catch (ExecutionException e) {
            // ignore
        } catch (TimeoutException e) {
            throw new RuntimeException("Running FileWatcher wasn't stopped in timeout limits.", e);
        } finally {
            execution = null;
        }
    }
}
