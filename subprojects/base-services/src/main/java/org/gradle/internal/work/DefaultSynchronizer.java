/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.Factory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class DefaultSynchronizer implements Synchronizer {
    private final WorkerLeaseService workerLeaseService;
    private final Lock lock = new ReentrantLock();

    public DefaultSynchronizer(WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public void withLock(Runnable action) {
        takeOwnership();
        try {
            action.run();
        } finally {
            releaseOwnership();
        }
    }

    @Override
    public <T> T withLock(Factory<T> action) {
        takeOwnership();
        try {
            return action.create();
        } finally {
            releaseOwnership();
        }
    }

    private void takeOwnership() {
        if (!workerLeaseService.isWorkerThread()) {
            throw new IllegalStateException("The current thread is not registered as a worker thread.");
        }
        workerLeaseService.blocking(new Runnable() {
            @Override
            public void run() {
                lock.lock();
            }
        });
    }

    private void releaseOwnership() {
        lock.unlock();
    }
}
