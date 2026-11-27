/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.test.fixtures.ConcurrentTestUtil

import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Holds the first thread to pass through a section until another thread contends with it.
 */
class ContentionGate {

    private final long timeoutSeconds
    private final CountDownLatch released = new CountDownLatch(1)
    private final AtomicInteger entryCount = new AtomicInteger()
    private final AtomicInteger insideCount = new AtomicInteger()
    private final AtomicInteger maxInsideCount = new AtomicInteger()
    private volatile Thread holder

    ContentionGate(long timeoutSeconds = 10) {
        this.timeoutSeconds = timeoutSeconds
    }

    /**
     * Runs the section, holding the first caller at its start until released;
     * later callers are not held.
     */
    public <T> T pass(Closure<T> section) {
        int inside = insideCount.incrementAndGet()
        maxInsideCount.updateAndGet { Math.max(it, inside) }
        try {
            if (entryCount.incrementAndGet() == 1) {
                holder = Thread.currentThread()
                // Shorter than the 20 seconds that `ConcurrentSpec.async` waits,
                // so this failure is reported instead
                long holdMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds) * 3 / 2
                if (!released.await(holdMillis, TimeUnit.MILLISECONDS)) {
                    throw new AssertionError("The first thread was never released")
                }
            }
            return section.call()
        } finally {
            insideCount.decrementAndGet()
        }
    }

    /**
     * Number of threads that entered the section.
     */
    int getEntries() {
        entryCount.get()
    }

    /**
     * Highest number of threads that were inside the section at the same time.
     */
    int getMaxConcurrentEntries() {
        maxInsideCount.get()
    }

    /**
     * Releases the first thread once another thread waits for any lock it holds, or enters as well.
     */
    void releaseWhenContended() {
        releaseWhen { Thread first -> anyThreadWaitsForLockOf(first) }
    }

    /**
     * Releases the first thread once the condition holds for it, or another thread enters as well.
     */
    void releaseWhen(Closure<Boolean> contended) {
        if (released.count == 0) {
            throw new IllegalStateException("The first thread was already released")
        }
        try {
            ConcurrentTestUtil.poll(timeoutSeconds) {
                def first = holder
                assert first != null : "No thread entered the section"
                assert entryCount.get() > 1 || contended(first) : "No thread contended with the first one"
            }
        } finally {
            released.countDown()
        }
    }

    private static boolean anyThreadWaitsForLockOf(Thread owner) {
        def threads = ManagementFactory.threadMXBean
        // Dead threads are reported as null
        threads.getThreadInfo(threads.allThreadIds).any { it != null && it.lockOwnerId == owner.id }
    }
}
