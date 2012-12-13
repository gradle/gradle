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
class Instants {
    private final Object lock = new Object()
    private final Map<String, NamedInstant> timePoints = [:]
    private final TestExecutor executor

    Instants(TestExecutor executor) {
        this.executor = executor
    }

    @Override
    String toString() {
        return "instants"
    }

    void waitFor(String name) {
        synchronized (lock) {
            while (!timePoints.containsKey(name)) {
                lock.wait()
            }
        }
    }

    def getProperty(String name) {
        def testThread = executor.testThread
        synchronized (lock) {
            def time = timePoints[name]
            if (testThread) {
                if (time != null) {
                    throw new IllegalStateException("Instant '$name' has already been defined by another test thread.")
                }
                def now = System.nanoTime()
                time = new NamedInstant(name, now, timePoints.size())
                timePoints[name] = time
                lock.notifyAll()
                println "* instant $name reached"
                return time
            } else {
                if (time == null) {
                    throw new IllegalStateException("Instant '$name' has not been defined by any test thread.")
                }
                return time
            }
        }
    }
}
