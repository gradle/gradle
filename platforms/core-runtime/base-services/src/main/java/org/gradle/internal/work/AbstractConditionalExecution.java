/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.work;

import org.gradle.internal.UncheckedException;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

public abstract class AbstractConditionalExecution<T> implements ConditionalExecution<T> {
    private final CountDownLatch finished = new CountDownLatch(1);
    private final RunnableFuture<T> runnable;

    public AbstractConditionalExecution(final Callable<T> callable) {
        this.runnable = new FutureTask<T>(callable);
    }

    @Override
    public Runnable getExecution() {
        return runnable;
    }

    @Override
    public T await() {
        boolean interrupted = false;
        while (true) {
            try {
                finished.await();
                break;
            } catch (InterruptedException e) {
                cancel();
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        try {
            return runnable.get();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    @Override
    public void complete() {
        finished.countDown();
    }

    @Override
    public boolean isComplete() {
        return finished.getCount() == 0;
    }

    @Override
    public void cancel() {
        runnable.cancel(true);
    }

}
