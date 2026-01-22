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
package org.gradle.cache.internal

import org.gradle.cache.CacheCleanupStrategy
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.FineGrainedCacheCleanupStrategy
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DefaultFineGrainedPersistentCacheConcurrencyTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def cacheDir = tmpDir.file("fine-grained-cache")

    def cacheCleanupStrategy = new FineGrainedCacheCleanupStrategy() {
        @Override
        CacheCleanupStrategy getCleanupStrategy() {
            return CacheCleanupStrategy.NO_CLEANUP
        }
    }

    FileLockManager lockManager = Mock(FileLockManager)

    @Subject
    @AutoCleanup
    def cache = new DefaultFineGrainedPersistentCache(cacheDir, "<display>", lockManager, cacheCleanupStrategy)

    def "useCache does not block for different keys when used concurrently"() {
        given:
        def key1 = "k1"
        def key2 = "k2"
        cache.open()

        // Allow any number of lock acquisitions; return a fresh mock each time
        _ * lockManager.lock(_, _, _, _) >> { args -> Mock(FileLock) }

        // Latches to coordinate and prove overlap
        def bothStarted = new CountDownLatch(2)
        def allowFinish = new CountDownLatch(1)
        def overlapped = new AtomicBoolean(false)

        when:
        Thread t1 = new Thread({
            cache.useCache(key1) {
                bothStarted.countDown()
                // wait until both threads started
                bothStarted.await(5, TimeUnit.SECONDS)
                // At this point, if t2 also started, we are overlapping
                overlapped.set(true)
                allowFinish.await(5, TimeUnit.SECONDS)
            }
        }, "fg-cache-t1")

        Thread t2 = new Thread({
            cache.useCache(key2) {
                bothStarted.countDown()
                // wait until both threads started
                bothStarted.await(5, TimeUnit.SECONDS)
                overlapped.set(true)
                allowFinish.await(5, TimeUnit.SECONDS)
            }
        }, "fg-cache-t2")
        t1.start()
        t2.start()

        then:
        bothStarted.await(5, TimeUnit.SECONDS)

        when:
        allowFinish.countDown()
        t1.join(5000)
        t2.join(5000)

        then:
        overlapped.get()
    }

    def "useCache blocks for the same key when used concurrently"() {
        given:
        def key = "same"
        cache.open()

        // Allow any number of lock acquisitions; return a fresh mock each time
        _ * lockManager.lock(_, _, _, _) >> { args -> Mock(FileLock) }

        def firstEntered = new CountDownLatch(1)
        def allowSecondToProceed = new CountDownLatch(1)
        def secondEntered = new CountDownLatch(1)
        def secondCompleted = new CountDownLatch(1)
        def overlapped = new AtomicBoolean(false)
        def runningKeyActionCounter = new AtomicInteger()

        when:
        Thread t1 = new Thread({
            cache.useCache(key) {
                runningKeyActionCounter.incrementAndGet()
                firstEntered.countDown()
                // hold the lock to block the second thread
                allowSecondToProceed.await(5, TimeUnit.SECONDS)
            }
        }, "fg-cache-same-t1")

        Thread t2 = new Thread({
            // t2 should block on useCache until t1 finishes
            cache.useCache(key) {
                runningKeyActionCounter.incrementAndGet()
                // if we reach here before t1 released, then they overlapped (which should not happen)
                overlapped.set(firstEntered.count == 0 && allowSecondToProceed.count != 0)
                secondEntered.countDown()
            }
            secondCompleted.countDown()
        }, "fg-cache-same-t2")

        t1.start()
        // ensure first thread is inside its action holding the lock
        assert firstEntered.await(5, TimeUnit.SECONDS)
        t2.start()

        then:
        // Give the second thread a little time to attempt entry; it should NOT enter yet
        !secondEntered.await(300, TimeUnit.MILLISECONDS)

        when:
        // release the first thread so the second can proceed
        allowSecondToProceed.countDown()
        t1.join(5000)
        // now the second should be able to enter and complete
        assert secondEntered.await(5, TimeUnit.SECONDS)
        t2.join(5000)

        then:
        secondCompleted.await(1, TimeUnit.SECONDS)
        runningKeyActionCounter.get() == 2
        !overlapped.get()
    }

    def "withFileLock does not block for the same key when used concurrently"() {
        given:
        def key = "same"
        cache.open()

        // Allow any number of lock acquisitions; return a fresh mock each time (no blocking via file locks in this test)
        _ * lockManager.lock(_, _, _, _) >> { args -> Mock(FileLock) }

        def bothStarted = new CountDownLatch(2)
        def allowFinish = new CountDownLatch(1)
        def overlapped = new AtomicBoolean(false)

        when:
        Thread t1 = new Thread({
            cache.withFileLock(key) {
                bothStarted.countDown()
                // wait until the other thread also starts its action for the same key
                bothStarted.await(5, TimeUnit.SECONDS)
                overlapped.set(true)
                allowFinish.await(5, TimeUnit.SECONDS)
            }
        }, "fg-cache-wfl-same-t1")

        Thread t2 = new Thread({
            cache.withFileLock(key) {
                bothStarted.countDown()
                bothStarted.await(5, TimeUnit.SECONDS)
                overlapped.set(true)
                allowFinish.await(5, TimeUnit.SECONDS)
            }
        }, "fg-cache-wfl-same-t2")
        t1.start()
        t2.start()

        then:
        bothStarted.await(5, TimeUnit.SECONDS)

        when:
        allowFinish.countDown()
        t1.join(5000)
        t2.join(5000)

        then:
        overlapped.get()
    }
}
