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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WatchServiceFileWatcherBacking {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchServiceFileWatcherBacking.class);

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();

    private final Action<? super Throwable> onError;
    private final FileWatcherListener listener;
    private final WatchService watchService;
    private final WatchServicePoller poller;

    private final FileWatcher fileWatcher = new FileWatcher() {
        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public void stop() {
            WatchServiceFileWatcherBacking.this.stop();
        }
    };

    WatchServiceFileWatcherBacking(FileSystemSubset fileSystemSubset, Action<? super Throwable> onError, FileWatcherListener listener, WatchService watchService) throws IOException {
        this.onError = onError;
        this.listener = new WatchServiceRegistrar(watchService, fileSystemSubset, listener);
        this.watchService = watchService;
        this.poller = new WatchServicePoller(watchService);
    }

    public FileWatcher start(ListeningExecutorService executorService) {
        if (started.compareAndSet(false, true)) {
            final ListenableFuture<?> runLoopFuture = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    if (!stopped.get()) {
                        running.set(true);
                        try {
                            try {
                                pumpEvents();
                            } catch (InterruptedException e) {
                                // just stop
                            } catch (Throwable t) {
                                stop();
                                onError.execute(t);
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
                stop();
            }
        }
    }

    private void deliverEvents(List<FileWatcherEvent> events) {
        for (FileWatcherEvent event : events) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Received file system event: {}", event);
            }
            if (!isRunning()) {
                break;
            }
            listener.onChange(fileWatcher, event);
        }
    }

    private boolean isRunning() {
        return running.get() && !Thread.currentThread().isInterrupted();
    }

    private void stop() {
        if (stopped.compareAndSet(false, true)) {
            if (running.compareAndSet(true, false)) {
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

}
