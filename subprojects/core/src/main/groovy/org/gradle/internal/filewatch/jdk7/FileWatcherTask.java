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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class FileWatcherTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWatcherTask.class);
    static final int POLL_TIMEOUT_MILLIS = 250;
    private final AtomicBoolean runningFlag;
    private final FileWatcherChangesNotifier changesNotifier;
    private final WatchStrategy watchStrategy;
    private final DirTreeWatchRegistry dirTreeWatchRegistry;
    private final IndividualFileWatchRegistry individualFileWatchRegistry;

    public FileWatcherTask(WatchStrategy watchStrategy, FileWatchInputs inputs, AtomicBoolean runningFlag, Runnable callback) throws IOException {
        this.changesNotifier = createChangesNotifier(callback);
        this.runningFlag = runningFlag;
        this.watchStrategy = watchStrategy;
        this.dirTreeWatchRegistry = DirTreeWatchRegistry.create(watchStrategy);
        this.individualFileWatchRegistry = new IndividualFileWatchRegistry(watchStrategy);
        registerWatches(inputs);
    }

    protected FileWatcherChangesNotifier createChangesNotifier(Runnable callback) {
        return new FileWatcherChangesNotifier(callback);
    }

    private void registerWatches(FileWatchInputs inputs) throws IOException {
        dirTreeWatchRegistry.register(inputs.getDirectoryTrees());
        individualFileWatchRegistry.register(inputs.getFiles());
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
        changesNotifier.reset();
        while (watchLoopRunning()) {
            watchStrategy.pollChanges(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS, new WatchHandler() {
                @Override
                public void onActivation() {
                    changesNotifier.eventReceived();
                }

                @Override
                public void onOverflow() {
                    changesNotifier.notifyChanged();
                }

                @Override
                public void onChange(ChangeDetails changeDetails) {
                    dirTreeWatchRegistry.handleChange(changeDetails, changesNotifier);
                    individualFileWatchRegistry.handleChange(changeDetails, changesNotifier);
                }
            });
            changesNotifier.handlePendingChanges();
        }
    }

    protected boolean watchLoopRunning() {
        return runningFlag.get() && !Thread.currentThread().isInterrupted();
    }
}
