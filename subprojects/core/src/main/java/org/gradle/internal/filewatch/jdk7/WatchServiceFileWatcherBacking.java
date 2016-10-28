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

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherListener;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WatchServiceFileWatcherBacking {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchServiceFileWatcherBacking.class);

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicReference<SoftReference<Thread>> pollerThreadReference = new AtomicReference<SoftReference<Thread>>();

    private final Action<? super Throwable> onError;
    private final WatchServiceRegistrar watchServiceRegistrar;
    private final WatchService watchService;
    private final WatchServicePoller poller;

    private final FileWatcher fileWatcher = new FileWatcher() {
        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public void watch(FileSystemSubset fileSystemSubset) throws IOException {
            WatchServiceFileWatcherBacking.this.watchServiceRegistrar.watch(fileSystemSubset);
        }

        @Override
        public void stop() {
            WatchServiceFileWatcherBacking.this.stop();
        }
    };

    WatchServiceFileWatcherBacking(Action<? super Throwable> onError, FileWatcherListener listener, WatchService watchService, FileSystem fileSystem) throws IOException {
        this(onError, listener, watchService, new WatchServiceRegistrar(watchService, listener, fileSystem));
    }

    WatchServiceFileWatcherBacking(Action<? super Throwable> onError, FileWatcherListener listener, WatchService watchService, WatchServiceRegistrar watchServiceRegistrar) throws IOException {
        this.onError = onError;
        this.watchServiceRegistrar = watchServiceRegistrar;
        this.watchService = watchService;
        this.poller = new WatchServicePoller(watchService);
    }

    public FileWatcher start(ListeningExecutorService executorService) {
        if (started.compareAndSet(false, true)) {
            final ListenableFuture<?> runLoopFuture = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    if (!stopped.get()) {
                        pollerThreadReference.set(new SoftReference<Thread>(Thread.currentThread()));
                        running.set(true);
                        try {
                            try {
                                pumpEvents();
                            } catch (InterruptedException e) {
                                // just stop
                            } catch (Throwable t) {
                                if (!(Throwables.getRootCause(t) instanceof InterruptedException)) {
                                    stop();
                                    onError.execute(t);
                                }
                            }
                        } finally {
                            stop();
                        }
                    }
                }
            });

            // This is necessary so that the watcher indicates its not running if the runnable gets cancelled
            Futures.addCallback(runLoopFuture, new FutureCallback<Object>() {
                @Override
                public void onSuccess(Object result) {
                    running.set(false);
                }

                @Override
                public void onFailure(Throwable t) {
                    running.set(false);
                }
            });
            return fileWatcher;
        } else {
            throw new IllegalStateException("file watcher is started");
        }
    }

    private void pumpEvents() throws InterruptedException {
        while (isRunning()) {
            try {
                List<FileWatcherEvent> events = poller.takeEvents();
                if (events != null) {
                    deliverEvents(events);
                }
            } catch (ClosedWatchServiceException e) {
                LOGGER.debug("Received ClosedWatchServiceException, stopping");
                stop();
            }
        }
    }

    private void deliverEvents(List<FileWatcherEvent> events) {
        for (FileWatcherEvent event : events) {
            if (!isRunning()) {
                LOGGER.debug("File watching isn't running, breaking out of event delivery.");
                break;
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Received file system event: {}", event);
            }
            watchServiceRegistrar.onChange(fileWatcher, event);
        }
    }

    private boolean isRunning() {
        return running.get() && !Thread.currentThread().isInterrupted();
    }

    private void stop() {
        if (stopped.compareAndSet(false, true)) {
            if (running.compareAndSet(true, false)) {
                LOGGER.debug("Stopping file watching");
                interruptPollerThread();
                try {
                    watchService.close();
                } catch (IOException e) {
                    // ignore exception in shutdown
                } catch (ClosedWatchServiceException e) {
                    // ignore
                }
            }
        }
    }

    private void interruptPollerThread() {
        SoftReference<Thread> threadSoftReference = pollerThreadReference.getAndSet(null);
        if (threadSoftReference != null) {
            Thread pollerThread = threadSoftReference.get();
            if (pollerThread != null && pollerThread != Thread.currentThread()) {
                // only interrupt poller thread if it's not current thread
                LOGGER.debug("Interrupting poller thread '{}'", pollerThread.getName());
                pollerThread.interrupt();
            }
        }
    }

}
