/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.test.fixtures.concurrent

import org.gradle.internal.concurrent.ManagedExecutor

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class TestManagedExecutor extends AbstractExecutorService implements ManagedExecutor {
    private final Lock lock = new ReentrantLock()
    private final Condition condition = lock.newCondition()
    private int count
    private final TestExecutor executor;

    TestManagedExecutor(TestExecutor executor) {
        this.executor = executor
    }

    void execute(Runnable command) {
        lock.lock()
        try {
            count++
        } finally {
            lock.unlock()
        }

        executor.execute {
            try {
                command.run()
            } finally {
                lock.lock()
                try {
                    count--
                    condition.signalAll()
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    void requestStop() {
    }

    void stop() {
        lock.lock()
        try {
            while (count > 0) {
                condition.await()
            }
        } finally {
            lock.unlock()
        }
    }

    void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
        throw new UnsupportedOperationException()
    }

    void shutdown() {
        throw new UnsupportedOperationException()
    }

    List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException()
    }

    boolean isShutdown() {
        throw new UnsupportedOperationException()
    }

    boolean isTerminated() {
        throw new UnsupportedOperationException()
    }

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException()
    }

    @Override
    void setFixedPoolSize(int numThreads) {
        throw new UnsupportedOperationException()
    }
}
