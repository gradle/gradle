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

package org.gradle.internal.work;

import org.gradle.internal.resources.ResourceLock;

import java.util.concurrent.Callable;

public class StopShieldingWorkerLeaseService implements WorkerLeaseService {

    private final WorkerLeaseService delegate;

    public StopShieldingWorkerLeaseService(WorkerLeaseService delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getMaxWorkerCount() {
        return delegate.getMaxWorkerCount();
    }

    @Override
    public <T> T withLocks(Iterable<? extends ResourceLock> locks, Callable<T> action) {
        return delegate.withLocks(locks, action);
    }

    @Override
    public void withLocks(Iterable<? extends ResourceLock> locks, Runnable action) {
        delegate.withLocks(locks, action);
    }

    @Override
    public <T> T withoutLocks(Iterable<? extends ResourceLock> locks, Callable<T> action) {
        return delegate.withoutLocks(locks, action);
    }

    @Override
    public void withoutLocks(Iterable<? extends ResourceLock> locks, Runnable action) {
        delegate.withoutLocks(locks, action);
    }

    @Override
    public WorkerLease getCurrentWorkerLease() {
        return delegate.getCurrentWorkerLease();
    }

    @Override
    public WorkerLease getWorkerLease() {
        return delegate.getWorkerLease();
    }

    @Override
    public void withSharedLease(WorkerLease sharedLease, Runnable action) {
        delegate.withSharedLease(sharedLease, action);
    }

    @Override
    public ResourceLock getProjectLock(String gradlePath, String projectPath) {
        return delegate.getProjectLock(gradlePath, projectPath);
    }

    @Override
    public <T> T withoutProjectLock(Callable<T> action) {
        return delegate.withoutProjectLock(action);
    }

    @Override
    public void withoutProjectLock(Runnable action) {
        delegate.withoutProjectLock(action);
    }

    @Override
    public void stop() {
        // noop
    }
}
