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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.test.fixtures.ConcurrentTestUtil

import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Holds the first discovery until a concurrent caller is shown to contend with it.
 */
class DiscoveryGate {
    private static final long TIMEOUT_SECONDS = 10

    private final CountDownLatch released = new CountDownLatch(1)
    private final AtomicInteger discoveryCount = new AtomicInteger()
    private volatile Thread discoverer

    /**
     * Records a discovery; call it from the stubbed discovery source.
     */
    void discovering() {
        if (discoveryCount.incrementAndGet() == 1) {
            discoverer = Thread.currentThread()
            released.await()
        }
    }

    int getDiscoveries() {
        discoveryCount.get()
    }

    /**
     * Releases the first discovery once another caller waits for its lock or discovers as well.
     */
    void releaseWhenContended() {
        try {
            ConcurrentTestUtil.poll(TIMEOUT_SECONDS) {
                assert discoverer != null : "No caller started discovery"
                assert discoveryCount.get() > 1 || anyThreadWaitsForLockOf(discoverer) : "No caller contended with the first discovery"
            }
        } finally {
            released.countDown()
        }
    }

    private static boolean anyThreadWaitsForLockOf(Thread owner) {
        ManagementFactory.threadMXBean.dumpAllThreads(false, false).any { it.lockOwnerId == owner.id }
    }
}
