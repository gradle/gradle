/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.AsyncWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.nativeplatform.internal.BinaryToolSpec;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

public abstract class AbstractAsyncCompiler<T extends BinaryToolSpec> extends AbstractCompiler<T> {
    private final BuildOperationExecutor buildOperationExecutor;
    private final CommandLineToolInvocationWorker commandLineToolInvocationWorker;
    private final NativeExecutor nativeExecutor;

    public AbstractAsyncCompiler(BuildOperationExecutor buildOperationExecutor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, ArgsTransformer<T> argsTransformer, boolean useCommandFile, NativeExecutor nativeExecutor) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, argsTransformer, useCommandFile);
        this.buildOperationExecutor = buildOperationExecutor;
        this.commandLineToolInvocationWorker = commandLineToolInvocationWorker;
        this.nativeExecutor = nativeExecutor;
    }

    @Override
    public WorkResult execute(final T spec) {
        final Action<BuildOperationQueue<CommandLineToolInvocation>> invocationAction = newInvocationAction(spec);

        ListenableFuture<WorkResult> futureWorkResult = nativeExecutor.submit(new Callable<WorkResult>() {
            @Override
            public WorkResult call() throws Exception {
                buildOperationExecutor.runAll(commandLineToolInvocationWorker, invocationAction);

                return new SimpleWorkResult(true);
            }
        });

        return new AsyncCompilerResult(futureWorkResult);
    }

    private static class AsyncCompilerResult implements AsyncWorkResult {
        private final ListenableFuture<WorkResult> future;
        private final Object lock = new Object();
        private volatile int callbackCount;

        AsyncCompilerResult(ListenableFuture<WorkResult> future) {
            this.future = future;
        }

        @Override
        public boolean isComplete() {
            return future.isDone();
        }

        @Override
        public boolean getDidWork() {
            try {
                return future.get().getDidWork();
            } catch (Exception e) {
                // Exceptions will be surfaced through waitForCompletion()
                return false;
            }
        }

        @Override
        public void waitForCompletion() {
            try {
                future.get();
                while (true) {
                    synchronized (lock) {
                        if (callbackCount == 0) {
                            break;
                        }
                        lock.wait();
                    }
                }
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void onCompletion(final Runnable callback) {
            synchronized (lock) {
                callbackCount++;
                final Runnable wrapped = new Runnable() {
                    @Override
                    public void run() {
                        callback.run();
                        synchronized (lock) {
                            callbackCount--;
                            lock.notifyAll();
                        }
                    }
                };
                Futures.addCallback(future, new FutureCallback<WorkResult>() {
                    @Override
                    public void onSuccess(@Nullable WorkResult result) {
                        wrapped.run();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        wrapped.run();
                    }
                });
            }
        }
    }
}
