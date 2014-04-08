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

/**
 * A dynamic collection of {@link NamedInstant} objects. When a property of this object is accessed from a test thread, a new instant is defined. When
 * accessed from the main thread, queries an existing instant, asserting that it exists.
 */
class Instants implements InstantFactory, OperationListener {
    private final Object lock = new Object()
    private final Map<String, NamedInstant> timePoints = [:]
    private Thread mainThread
    private final TestLogger logger
    private int operations
    private int instantTimeout

    Instants(TestLogger logger) {
        this.logger = logger
        instantTimeout = 12000
    }

    void setTimeout(int instantTimeout) {
        synchronized (lock) {
            this.instantTimeout = instantTimeout
        }
    }

    @Override
    String toString() {
        return "instants"
    }

    void operationStarted() {
        synchronized (lock) {
            operations++
        }
    }

    void operationFinished() {
        synchronized (lock) {
            operations--
            lock.notifyAll()
        }
    }

    void mainThread(Thread thread) {
        synchronized (lock) {
            mainThread = thread;
        }
    }

    void waitFor(String name) {
        synchronized (lock) {
            long expiry = System.currentTimeMillis() + instantTimeout;
            while (!timePoints.containsKey(name) && System.currentTimeMillis() < expiry) {
                logger.log "waiting for instant '$name' ..."
                lock.wait(expiry - System.currentTimeMillis())
            }
            if (timePoints.containsKey(name)) {
                return
            }
            throw new IllegalStateException("Timeout waiting for instant '$name' to be defined by another thread.")
        }
    }

    def getProperty(String name) {
        synchronized (lock) {
            if (Thread.currentThread() != mainThread) {
                return now(name)
            } else {
                return get(name)
            }
        }
    }

    NamedInstant get(String name) {
        synchronized (lock) {
            def time = timePoints[name]
            if (time == null) {
                throw new IllegalStateException("Instant '$name' has not been defined by any test thread.")
            }
            return time
        }
    }

    NamedInstant now(String name) {
        synchronized (lock) {
            def time = timePoints[name]
            if (time != null) {
                throw new IllegalStateException("Instant '$name' has already been defined by another test thread.")
            }
            def now = System.nanoTime()
            time = new NamedInstant(name, now, timePoints.size())
            timePoints[name] = time
            lock.notifyAll()
            logger.log "instant '$name' reached"
            return time
        }
    }
}
