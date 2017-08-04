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

package org.gradle.internal.filewatch;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildGateToken;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultFileSystemChangeWaiterFactory implements FileSystemChangeWaiterFactory {
    public static final String QUIET_PERIOD_SYSPROP = "org.gradle.internal.filewatch.quietperiod";
    public static final String TRIGGER_BUILD_SYSPROP = "org.gradle.internal.continuous.trigger";

    private final FileWatcherFactory fileWatcherFactory;
    private final long quietPeriodMillis;
    private final boolean triggeredBuild;

    public DefaultFileSystemChangeWaiterFactory(FileWatcherFactory fileWatcherFactory) {
        this(fileWatcherFactory, getDefaultQuietPeriod(), isTriggerBuild());
    }

    private static long getDefaultQuietPeriod() {
        return Long.getLong(QUIET_PERIOD_SYSPROP, 250L);
    }

    private static boolean isTriggerBuild() {
        return Boolean.getBoolean(TRIGGER_BUILD_SYSPROP);
    }

    public DefaultFileSystemChangeWaiterFactory(FileWatcherFactory fileWatcherFactory, long quietPeriodMillis, boolean triggeredBuild) {
        this.fileWatcherFactory = fileWatcherFactory;
        this.quietPeriodMillis = quietPeriodMillis;
        this.triggeredBuild = triggeredBuild;
    }

    @Override
    public FileSystemChangeWaiter createChangeWaiter(PendingChangesListener listener, BuildCancellationToken cancellationToken, BuildGateToken buildGateToken) {
        return new ChangeWaiter(fileWatcherFactory, listener, quietPeriodMillis, triggeredBuild, cancellationToken, buildGateToken);
    }

    private static class ChangeWaiter implements FileSystemChangeWaiter {
        private final long quietPeriodMillis;
        private final boolean triggeredBuild;
        private final BuildCancellationToken cancellationToken;
        private final BuildGateToken buildGateToken;
        private final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private final AtomicLong lastChangeAt = new AtomicLong(0);
        private final FileWatcher watcher;
        private final Action<Throwable> onError;
        private boolean watching;
        private FileWatcherEventListener eventListener;
        private final Collection<FileWatcherEvent> eventsBeforeListening = new ArrayList<FileWatcherEvent>();
        private final Lock eventDeliveryLock = new ReentrantLock();

        private ChangeWaiter(FileWatcherFactory fileWatcherFactory, final PendingChangesListener pendingChangesListener, long quietPeriodMillis, boolean triggeredBuild, BuildCancellationToken cancellationToken, BuildGateToken buildGateToken) {
            this.quietPeriodMillis = quietPeriodMillis;
            this.triggeredBuild = triggeredBuild;
            this.cancellationToken = cancellationToken;
            this.buildGateToken = buildGateToken;
            this.onError = new Action<Throwable>() {
                @Override
                public void execute(Throwable throwable) {
                    error.set(throwable);
                    signal(lock, condition);
                }
            };
            watcher = fileWatcherFactory.watch(
                onError,
                new FileWatcherListener() {
                    @Override
                    public void onChange(final FileWatcher watcher, FileWatcherEvent event) {
                        if (!(event.getType() == FileWatcherEvent.Type.MODIFY && event.getFile().isDirectory())) {
                            deliverEvent(event);
                            signal(lock, condition, new Runnable() {
                                @Override
                                public void run() {
                                    lastChangeAt.set(monotonicClockMillis());
                                    pendingChangesListener.onPendingChanges();
                                }
                            });
                        }
                    }
                }
            );
        }

        @Override
        public void watch(FileSystemSubset fileSystemSubset) {
            try {
                if (!fileSystemSubset.isEmpty()) {
                    watching = true;
                    watcher.watch(fileSystemSubset);
                }
            } catch (IOException e) {
                onError.execute(e);
            }
        }

        public void wait(Runnable notifier, FileWatcherEventListener eventListener) {
            Runnable cancellationHandler = new Runnable() {
                @Override
                public void run() {
                    signal(lock, condition);
                }
            };
            try {
                attachEventListener(eventListener);
                if (cancellationToken.isCancellationRequested()) {
                    return;
                }
                cancellationToken.addCallback(cancellationHandler);
                notifier.run();
                lock.lock();
                try {
                    long lastChangeAtValue = lastChangeAt.get();
                    while (!cancellationToken.isCancellationRequested() && waitingForChanges(lastChangeAtValue)) {
                        condition.await(quietPeriodMillis, TimeUnit.MILLISECONDS);
                        lastChangeAtValue = lastChangeAt.get();
                    }
                } finally {
                    lock.unlock();
                }
                Throwable throwable = error.get();
                if (throwable != null) {
                    throw throwable;
                }
                if (triggeredBuild) {
                    buildGateToken.waitForOpen();
                }
            } catch (Throwable e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                detachEventListener();
                cancellationToken.removeCallback(cancellationHandler);
                watcher.stop();
            }
        }

        private boolean waitingForChanges(long lastChangeAtValue) {
            return error.get() == null && shouldKeepWaitingForQuietPeriod(lastChangeAtValue);
        }

        private void deliverEvent(FileWatcherEvent event) {
            eventDeliveryLock.lock();
            try {
                if (eventListener != null) {
                    eventListener.onChange(event);
                } else {
                    eventsBeforeListening.add(event);
                }
            } finally {
                eventDeliveryLock.unlock();
            }
        }

        private void attachEventListener(FileWatcherEventListener eventListener) {
            eventDeliveryLock.lock();
            try {
                this.eventListener = eventListener;
                for (FileWatcherEvent event : eventsBeforeListening) {
                    eventListener.onChange(event);
                }
                eventsBeforeListening.clear();
            } finally {
                eventDeliveryLock.unlock();
            }
        }

        private void detachEventListener() {
            eventDeliveryLock.lock();
            try {
                this.eventListener = null;
            } finally {
                eventDeliveryLock.unlock();
            }
        }

        private boolean shouldKeepWaitingForQuietPeriod(long lastChangeAtValue) {
            long now = monotonicClockMillis();
            return lastChangeAtValue == 0   // no changes yet
                || now < lastChangeAtValue  // handle case where monotonic clock isn't monotonic
                || now - lastChangeAtValue < quietPeriodMillis;
        }

        @Override
        public boolean isWatching() {
            return watching;
        }

        @Override
        public void stop() {
            watcher.stop();
        }
    }

    private static long monotonicClockMillis() {
        return System.nanoTime() / 1000000L;
    }

    private static void signal(Lock lock, Condition condition, Runnable runnable) {
        boolean interrupted = Thread.interrupted();
        lock.lock();
        try {
            runnable.run();
            condition.signal();
        } finally {
            lock.unlock();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void signal(Lock lock, Condition condition) {
        signal(lock, condition, new Runnable() {
            @Override
            public void run() {

            }
        });
    }

}
