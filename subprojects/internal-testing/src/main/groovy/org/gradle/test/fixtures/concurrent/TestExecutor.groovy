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

import java.util.concurrent.Executor
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class TestExecutor implements Executor {
    private final Lock lock = new ReentrantLock()
    private final Condition condition = lock.newCondition()
    private final Set<Thread> threads = new HashSet<Thread>()
    private final TestLogger logger
    private Thread owner
    private Throwable failure
    private int threadNum

    TestExecutor(TestLogger logger) {
        this.logger = logger
    }

    void start() {
        lock.lock()
        try {
            if (owner != null) {
                throw new IllegalStateException("Cannot nest async { } blocks.")
            }
            owner = Thread.currentThread()
        } finally {
            lock.unlock()
        }
    }

    void execute(Runnable runnable) {
        def thread = new Thread() {
            @Override
            void run() {
                try {
                    logger.log "running"
                    runnable.run()
                } catch (Throwable throwable) {
                    logger.log "failed"
                    lock.lock()
                    try {
                        if (failure == null) {
                            failure = throwable
                        }
                    } finally {
                        lock.unlock()
                    }
                } finally {
                    logger.log "finished"
                    lock.lock()
                    try {
                        threads.remove(Thread.currentThread())
                        condition.signalAll()
                    } finally {
                        lock.unlock()
                    }
                }
            }
        }

        lock.lock()
        try {
            threads << thread
            thread.name = "Test thread ${++threadNum}"
        } finally {
            lock.unlock()
        }

        thread.start()
    }

    void stop(Date expiry) {
        lock.lock()
        try {
            if (!threads.isEmpty()) {
                logger.log "waiting for ${threads.size()} test threads to complete."
            }

            while (!threads.isEmpty()) {
                if (!condition.awaitUntil(expiry)) {
                    break;
                }
            }

            if (!threads.isEmpty()) {
                logger.log "timeout waiting for ${threads.size()} threads to complete"
                threads.each { thread ->
                    IllegalStateException e = new IllegalStateException("Timeout waiting for ${thread.name} to complete.")
                    e.stackTrace = thread.stackTrace
                    if (failure == null) {
                        failure = e
                    } else {
                        e.printStackTrace()
                    }
                    thread.interrupt()
                }
            }
            threads.clear()
            if (failure != null) {
                throw failure
            }
        } finally {
            owner = null
            failure = null
            lock.unlock()
        }
    }
}
