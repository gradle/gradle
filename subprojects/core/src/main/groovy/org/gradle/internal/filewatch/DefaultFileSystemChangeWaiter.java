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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultFileSystemChangeWaiter implements FileSystemChangeWaiter {
    private final ExecutorFactory executorFactory;
    private final FileWatcherFactory fileWatcherFactory;
    private final long quietPeriodMillis;

    public DefaultFileSystemChangeWaiter(ExecutorFactory executorFactory, FileWatcherFactory fileWatcherFactory) {
        this(executorFactory, fileWatcherFactory, 250L);
    }

    public DefaultFileSystemChangeWaiter(ExecutorFactory executorFactory, FileWatcherFactory fileWatcherFactory, long quietPeriodMillis) {
        this.executorFactory = executorFactory;
        this.fileWatcherFactory = fileWatcherFactory;
        this.quietPeriodMillis = quietPeriodMillis;
    }

    @Override
    public void wait(FileSystemSubset taskFileSystemInputs, final BuildCancellationToken cancellationToken, Runnable notifier) {
        if (cancellationToken.isCancellationRequested()) {
            return;
        }

        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final StoppableExecutor executorService = executorFactory.create("continuous build - wait");

        final Lock lock = new ReentrantLock();
        final Condition condition = lock.newCondition();
        final AtomicLong lastChangeAt = new AtomicLong(0);

        Runnable cancellationHandler = new Runnable() {
            @Override
            public void run() {
                signal(lock, condition);
            }
        };


        FileWatcher watcher = fileWatcherFactory.watch(
            taskFileSystemInputs,
            new Action<Throwable>() {
                @Override
                public void execute(Throwable throwable) {
                    error.set(throwable);
                    signal(lock, condition);
                }
            },
            new FileWatcherListener() {
                @Override
                public void onChange(final FileWatcher watcher, FileWatcherEvent event) {
                    if (!(event.getType() == FileWatcherEvent.Type.MODIFY && event.getFile().isDirectory())) {
                        signal(lock, condition, new Runnable() {
                            @Override
                            public void run() {
                                lastChangeAt.set(System.currentTimeMillis());
                            }
                        });
                    }
                }
            }
        );

        try {
            cancellationToken.addCallback(cancellationHandler);
            notifier.run();
            lock.lock();
            try {
                long lastChangeAtValue = lastChangeAt.get();
                while (!cancellationToken.isCancellationRequested() && error.get()==null && (lastChangeAtValue == 0 || System.currentTimeMillis() - lastChangeAtValue < quietPeriodMillis)) {
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
        } catch (Throwable e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            cancellationToken.removeCallback(cancellationHandler);
            CompositeStoppable.stoppable(watcher, executorService).stop();
        }
    }

    private void signal(Lock lock, Condition condition, Runnable runnable) {
        lock.lock();
        try {
            runnable.run();
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    private void signal(Lock lock, Condition condition) {
        signal(lock, condition, new Runnable() {
            @Override
            public void run() {

            }
        });
    }

}
