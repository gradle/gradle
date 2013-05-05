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
class Instants implements InstantFactory, TestThreadListener, OperationListener {
    private final Object lock = new Object()
    private final Map<String, NamedInstant> timePoints = [:]
    private final Set<Thread> testThreads = new HashSet<Thread>()
    private int operations

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

    void threadStarted(Thread thread) {
        synchronized (lock) {
            testThreads.add(thread)
        }
    }

    void threadFinished(Thread thread) {
        synchronized (lock) {
            testThreads.remove(thread)
            lock.notifyAll()
        }
    }

    void waitFor(String name) {
        long expiry = System.currentTimeMillis() + 12000;
        synchronized (lock) {
            while (!timePoints.containsKey(name) && System.currentTimeMillis() < expiry) {
                println "* ${Thread.currentThread().name} waiting for instant '$name' ..."
                if (testThreads.empty && operations == 0) {
                    throw new IllegalStateException("Cannot wait for instant '$name', as it has not been defined and no test threads are currently running.")
                }
                if (testThreads.size() == 1 && testThreads.contains(Thread.currentThread()) && operations == 0) {
                    throw new IllegalStateException("Cannot wait for instant '$name', as it has not been defined and no other test threads are currently running.")
                }
                lock.wait(expiry - System.currentTimeMillis())
            }
            if (timePoints.containsKey(name)) {
                return
            }
            throw new IllegalStateException("Timeout waiting for instant '$name' to be defined by another test thread.")
        }
    }

    def getProperty(String name) {
        synchronized (lock) {
            def testThread = testThreads.contains(Thread.currentThread())
            if (testThread) {
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
            println "* ${Thread.currentThread().name} instant $name reached"
            return time
        }
    }
}
