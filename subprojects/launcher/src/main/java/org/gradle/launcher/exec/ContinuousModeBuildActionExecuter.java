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

package org.gradle.launcher.exec;

import com.google.common.util.concurrent.*;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.internal.tasks.TaskFileSystemInputsAccumulator;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.filewatch.FileWatcherListener;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.SingleMessageLogger;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ContinuousModeBuildActionExecuter implements BuildExecuter {

    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final Logger logger;
    private final Action<? super Runnable> waiter;
    private final AtomicBoolean cancellationRequestedCheck;

    public ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, final ServiceRegistry services) {
        this(delegate, new Waiter(services));
    }

    ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, Action<? super Runnable> waiter) {
        this.delegate = delegate;
        this.waiter = waiter;
        this.logger = Logging.getLogger(ContinuousModeBuildActionExecuter.class);
        this.cancellationRequestedCheck = (waiter instanceof Waiter) ? ((Waiter)waiter).getCancellationRequested() : new AtomicBoolean(false);
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        if (continuousModeEnabled(actionParameters)) {
            SingleMessageLogger.incubatingFeatureUsed("Continuous mode");
            return executeMultipleBuilds(action, requestContext, actionParameters);
        }
        return executeSingleBuild(action, requestContext, actionParameters);
    }

    private Object executeMultipleBuilds(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        Object lastResult = null;
        int counter = 0;
        while (buildNotStopped(requestContext) && !cancellationRequestedCheck.get()) {
            if (++counter != 1) {
                // reset the time the build started so the total time makes sense
                requestContext.getBuildTimeClock().reset();
            }

            try {
                lastResult = executeSingleBuild(action, requestContext, actionParameters);
            } catch (Throwable t) {
                // TODO: logged already, are there certain cases we want to escape from this loop?
            }

            if (buildNotStopped(requestContext)) {
                waiter.execute(new Runnable() {
                    @Override
                    public void run() {
                        logger.lifecycle("Waiting for a trigger. To exit 'continuous mode', use Ctrl+D.");
                    }
                });
            }
        }

        logger.lifecycle("Build cancelled, exiting 'continuous mode'.");
        return lastResult;
    }

    private Object executeSingleBuild(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        return delegate.execute(action, requestContext, actionParameters);
    }

    private boolean continuousModeEnabled(BuildActionParameters actionParameters) {
        return actionParameters.isContinuousModeEnabled();
    }

    private boolean buildNotStopped(BuildRequestContext requestContext) {
        return !requestContext.getCancellationToken().isCancellationRequested();
    }

    private static class Waiter implements Action<Runnable> {
        private final ServiceRegistry services;
        private final ListeningExecutorService keyboardHandlerExecutor;

        private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);

        public Waiter(ServiceRegistry services) {
            this.services = services;
            this.keyboardHandlerExecutor = MoreExecutors.listeningDecorator(services.get(ExecutorFactory.class).create("Continuous mode keyboard handler"));
        }

        @Override
        public void execute(Runnable runnable) {
            FileSystemSubset taskFileSystemInputs = services.get(TaskFileSystemInputsAccumulator.class).get();
            FileWatcherFactory fileWatcherFactory = services.get(FileWatcherFactory.class);

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

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
                    @Override
                    public void onChange(FileWatcher watcher, FileWatcherEvent event) {
                        watcher.stop();
                        latch.countDown();
                    }
                }
            );

            try {
                runnable.run();
            } catch (Exception e) {
                watcher.stop();
                throw UncheckedException.throwAsUncheckedException(e);
            }

            ListenableFuture<Boolean> keyboardHandlerFuture = submitAsyncKeyboardHandler(latch);

            try {
                latch.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                if(!keyboardHandlerFuture.isDone()) {
                    keyboardHandlerFuture.cancel(true);
                } else if(Futures.getUnchecked(keyboardHandlerFuture)) {
                    cancellationRequested.set(true);
                }
            }

            Throwable throwable = error.get();
            if (throwable != null) {
                throw UncheckedException.throwAsUncheckedException(throwable);
            }

        }

        private ListenableFuture<Boolean> submitAsyncKeyboardHandler(final CountDownLatch latch) {
            ListenableFuture<Boolean> keyboardHandlerFuture = keyboardHandlerExecutor.submit(new KeyboardBreakHandler());
            Futures.addCallback(keyboardHandlerFuture, new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    if(result) {
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    latch.countDown();
                }
            });
            return keyboardHandlerFuture;
        }

        public AtomicBoolean getCancellationRequested() {
            return cancellationRequested;
        }
    }

    private static class KeyboardBreakHandler implements Callable<Boolean> {
        private static final int EOF = -1;
        private static final int KEY_CODE_CTRL_D = 4;

        @Override
        public Boolean call() {
            Boolean shouldTriggerLatch = waitForCtrlD();
            return shouldTriggerLatch;
        }

        private boolean waitForCtrlD() {
            int count = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int c = System.in.read();
                    if (c == KEY_CODE_CTRL_D) {
                        return true;
                    }
                    if (c == EOF) {
                        return count > 0;
                    } else {
                        count++;
                    }
                } catch (InterruptedIOException e) {
                    // expected exception when cancelled, just return
                    return true;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return true;
        }
    }
}
