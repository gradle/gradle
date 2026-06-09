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

import org.gradle.cache.FileLockManager
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.BlockingNotifier
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.cache.FileLockManager.LockMode.None
import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode

/**
 * Guards the fast/slow-path split in {@link DefaultCacheCoordinator#useCache}: the
 * {@link BlockingNotifier} must only be consulted when a thread actually has to wait for
 * in-process ownership, never on the uncontended (or reentrant) hot path.
 */
class CacheCoordinatorBlockingNotifierTest extends ConcurrentSpec {

    def notifier = new RecordingBlockingNotifier()
    def coordinator = new DefaultCacheCoordinator("<cache>", null, mode(None), null, Mock(FileLockManager), Mock(CacheInitializationAction), Mock(CacheCleanupExecutor), executorFactory, notifier)

    def setup() {
        coordinator.open()
    }

    def cleanup() {
        coordinator.close()
    }

    def "does not consult the notifier on the uncontended path"() {
        when:
        coordinator.useCache {
            // Reentrant use must not be treated as contention either.
            coordinator.useCache {}
        }

        then:
        notifier.count.get() == 0
    }

    def "consults the notifier when ownership is contended"() {
        when:
        async {
            start {
                coordinator.useCache {
                    instant.owned
                    // Stay owner until the waiter has entered the notifier's blocking section.
                    notifier.entered.await()
                }
            }
            start {
                thread.blockUntil.owned
                coordinator.useCache {
                    instant.waiterEntered
                }
            }
        }

        then:
        notifier.count.get() == 1
        instant.waiterEntered != null
    }

    static class RecordingBlockingNotifier implements BlockingNotifier {
        final AtomicInteger count = new AtomicInteger()
        final CountDownLatch entered = new CountDownLatch(1)

        @Override
        void blocking(Runnable action) {
            record()
            action.run()
        }

        @Override
        <T> T blocking(Factory<T> action) {
            record()
            return action.create()
        }

        private void record() {
            count.incrementAndGet()
            entered.countDown()
        }
    }
}
