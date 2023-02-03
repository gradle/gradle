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

package org.gradle.deployment.internal;

import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SimpleBlockingDeployment implements DeploymentInternal {
    private final Lock lock = new ReentrantLock();
    private final Condition upToDate = lock.newCondition();

    private final DeploymentInternal delegate;

    private boolean outOfDate;

    SimpleBlockingDeployment(DeploymentInternal delegate) {
        this.delegate = delegate;
    }

    @Override
    public Status status() {
        lock.lock();
        try {
            while (outOfDate) {
                try {
                    upToDate.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            return delegate.status();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void outOfDate() {
        lock.lock();
        try {
            delegate.outOfDate();
            outOfDate = true;
            upToDate.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void upToDate(@Nullable Throwable failure) {
        lock.lock();
        try {
            delegate.upToDate(failure);
            outOfDate = false;
            upToDate.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
