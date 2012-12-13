package org.gradle.test.fixtures.concurrent

import org.gradle.internal.concurrent.StoppableExecutor

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class TestStoppableExecutor implements StoppableExecutor {
    private final Lock lock = new ReentrantLock()
    private final Condition condition = lock.newCondition()
    private int count
    private final TestExecutor executor;

    TestStoppableExecutor(TestExecutor executor) {
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
}
