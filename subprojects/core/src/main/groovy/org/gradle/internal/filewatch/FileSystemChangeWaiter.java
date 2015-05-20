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

import com.google.common.util.concurrent.*;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.BiAction;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FileSystemChangeWaiter implements BiAction<FileSystemSubset, Runnable> {

    private static final long QUIET_PERIOD = 250L;

    private final ExecutorFactory executorFactory;
    private final FileWatcherFactory fileWatcherFactory;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);

    public FileSystemChangeWaiter(ExecutorFactory executorFactory, FileWatcherFactory fileWatcherFactory) {
        this.executorFactory = executorFactory;
        this.fileWatcherFactory = fileWatcherFactory;
    }

    @Override
    public void execute(FileSystemSubset taskFileSystemInputs, Runnable notifier) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final StoppableExecutor executorService = executorFactory.create("quiet period waiter");

        FileWatcher watcher = fileWatcherFactory.watch(
            taskFileSystemInputs,
            new Action<Throwable>() {
                @Override
                public void execute(Throwable throwable) {
                    error.set(throwable);
                    latch.countDown();
                }
            },
            new FileWatcherListener() {
                private IdleTimeout timeout;

                @Override
                public void onChange(final FileWatcher watcher, FileWatcherEvent event) {
                    if (timeout == null) {
                        timeout = new IdleTimeout(QUIET_PERIOD, new Runnable() {
                            @Override
                            public void run() {
                                watcher.stop();
                                latch.countDown();
                            }
                        });
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                timeout.await();
                            }
                        });
                    }
                    timeout.tick();
                }
            }
        );

        final StoppableExecutor keyboardHandlerExecutor = executorFactory.create("Continuous mode keyboard handler");
        ListenableFuture<Boolean> keyboardHandlerFuture = submitAsyncKeyboardHandler(MoreExecutors.listeningDecorator(keyboardHandlerExecutor), latch);

        try {
            notifier.run();
            latch.await();
            Throwable throwable = error.get();
            if (throwable != null) {
                throw UncheckedException.throwAsUncheckedException(throwable);
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            if (!keyboardHandlerFuture.isDone()) {
                keyboardHandlerFuture.cancel(true);
            } else if (Futures.getUnchecked(keyboardHandlerFuture)) {
                cancellationRequested.set(true);
            }
            CompositeStoppable.stoppable(watcher, executorService, keyboardHandlerExecutor).stop();
        }
    }

    private ListenableFuture<Boolean> submitAsyncKeyboardHandler(ListeningExecutorService keyboardHandlerExecutor, final CountDownLatch latch) {
        ListenableFuture<Boolean> keyboardHandlerFuture = keyboardHandlerExecutor.submit(new KeyboardBreakHandler());
        Futures.addCallback(keyboardHandlerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
                latch.countDown();
            }
        });
        return keyboardHandlerFuture;
    }

    private static class KeyboardBreakHandler implements Callable<Boolean> {
        private static final int EOF = -1;
        private static final int KEY_CODE_CTRL_D = 4;
        private static final long WAIT_INPUT_MILLIS = 250L;

        @Override
        public Boolean call() throws IOException {
            waitForCtrlD();
            return Boolean.TRUE;
        }

        private void waitForCtrlD() throws IOException {
            final InputStream inputStream = System.in;
            while (!Thread.currentThread().isInterrupted()) {
                int c = readWithoutBlocking(inputStream);
                if (c == KEY_CODE_CTRL_D || c == EOF) {
                    break;
                }
            }
        }

        private int readWithoutBlocking(InputStream inputStream) throws IOException {
            if (waitForAvailableInput(inputStream)) {
                return inputStream.read();
            } else {
                return EOF;
            }
        }

        private boolean waitForAvailableInput(InputStream inputStream) throws IOException {
            int available = 0;
            while (!Thread.currentThread().isInterrupted() && (available = inputStream.available()) == 0) {
                try {
                    Thread.sleep(WAIT_INPUT_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return available > 0;
        }
    }

    public AtomicBoolean getCancellationRequested() {
        return cancellationRequested;
    }
}
