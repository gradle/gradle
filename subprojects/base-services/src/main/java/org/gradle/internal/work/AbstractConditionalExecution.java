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
import org.gradle.internal.resources.ResourceLock;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public class AbstractConditionalExecution<T> implements ConditionalExecution<T> {
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Runnable runnable;
    private final ResourceLock resourceLock;

    private T result;
    private Throwable failure;

    public AbstractConditionalExecution(final Callable<T> callable, ResourceLock resourceLock) {
        this.runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    result = callable.call();
                } catch (Throwable t) {
                    registerFailure(t);
                }
            }
        };
        this.resourceLock = resourceLock;
    }

    @Override
    public ResourceLock getResourceLock() {
        return resourceLock;
    }

    @Override
    public Runnable getExecution() {
        return runnable;
    }

    @Override
    public T await() {
        try {
            finished.await();
            if (failure != null) {
                throw UncheckedException.throwAsUncheckedException(failure);
            } else {
                return result;
            }
        } catch(InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
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
    public void registerFailure(Throwable t) {
        this.failure = t;
    }
}
