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
import org.gradle.internal.filewatch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WatchServiceFileWatcher implements FileWatcher, Runnable, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchServiceFileWatcher.class);
    private static final int STOP_TIMEOUT_SECONDS = 10;
    private final FileWatcherListener listener;
    private final AtomicBoolean runningFlag = new AtomicBoolean(false);
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private final WatchService watchService;
    private final WatchServicePoller poller;
    private final WatchServiceRegistrar registrar;

    WatchServiceFileWatcher(Iterable<? extends File> roots, FileWatcherListener listener) throws IOException {
        this.listener = listener;
        this.watchService = FileSystems.getDefault().newWatchService();
        registrar = new WatchServiceRegistrar(watchService);
        for (File root : roots) {
            registrar.registerRoot(root.toPath());
        }
        poller = new WatchServicePoller(watchService);
    }

    public void run() {
        runningFlag.compareAndSet(false, true);
        try {
            try {
                pumpEvents();
            } catch (InterruptedException e) {
                // ignore exception in shutdown
            }
        } finally {
            stopRunning();
        }
    }

    private void stopRunning() {
        runningFlag.compareAndSet(true, false);
        try {
            watchService.close();
        } catch (IOException e) {
            // ignore exception in shutdown
        } finally {
            stopLatch.countDown();
        }
    }

    private void pumpEvents() throws InterruptedException {
        while (isRunning()) {
            List<FileWatcherEvent> events = poller.takeEvents();
            if (events != null) {
                deliverEvents(events);
            }
        }
    }

    private void deliverEvents(List<FileWatcherEvent> events) {
        for (FileWatcherEvent event : events) {
            deliverEvent(event);
        }
    }

    private void deliverEvent(FileWatcherEvent event) {
        handleNewDirectory(event);
        FileWatcherEventResult result = listener.onChange(event);
        if (result instanceof TerminateFileWatcherEventResult) {
            stopRunning();
            Runnable terminateAction = ((TerminateFileWatcherEventResult) result).getOnTerminateAction();
            if (terminateAction != null) {
                terminateAction.run();
            }
        }
    }

    private void handleNewDirectory(FileWatcherEvent event) {
        if (event.getType() == FileWatcherEvent.Type.CREATE && event.getFile().isDirectory()) {
            try {
                registrar.registerChild(event.getFile().toPath());
            } catch (IOException e) {
                LOGGER.warn("Problem adding watch to " + event.getFile(), e);
            }
        }
    }

    private boolean isRunning() {
        return runningFlag.get() && !Thread.currentThread().isInterrupted();
    }

    @Override
    public synchronized void stop() {
        if (runningFlag.compareAndSet(true, false)) {
            try {
                watchService.close();
            } catch (IOException e) {
                // ignore exception in shutdown
            }
            try {
                stopLatch.await(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignore exception in shutdown
            }
        }
    }
}
