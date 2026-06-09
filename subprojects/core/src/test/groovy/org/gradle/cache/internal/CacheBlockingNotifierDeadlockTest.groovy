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
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.DefaultWorkerLimits
import org.gradle.internal.work.ResourceLockStatistics
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import static org.gradle.cache.FileLockManager.LockMode.None
import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode
import static org.gradle.util.Path.path

/**
 * Reproduces the lock-order inversion between a cache's in-process ownership lock
 * ({@link DefaultCacheCoordinator}) and a project model-state lock that caused a parallel
 * configuration-cache store to hang. The two are wired through a real
 * {@link DefaultWorkerLeaseService} so that the fix — releasing the project lock while a
 * thread blocks for cache ownership — is exercised end to end rather than faked.
 *
 * <p>Without the fix, both feature methods deadlock and {@code async} times out.
 */
class CacheBlockingNotifierDeadlockTest extends ConcurrentSpec {

    def coordinationService = new DefaultResourceLockCoordinationService()
    def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, new DefaultWorkerLimits(2), ResourceLockStatistics.NO_OP)
    def blockingNotifier = new CacheBlockingNotifier()
    def coordinator = new DefaultCacheCoordinator("<cache>", null, mode(None), null, Mock(FileLockManager), Mock(CacheInitializationAction), Mock(CacheCleanupExecutor), executorFactory, blockingNotifier)

    def setup() {
        workerLeaseService.startProjectExecution(true)
        blockingNotifier.register(workerLeaseService)
        coordinator.open()
    }

    def cleanup() {
        coordinator.close()
        blockingNotifier.unregister(workerLeaseService)
        workerLeaseService.stop()
    }

    def "thread blocked for cache ownership releases its project lock so the owner can take it"() {
        when:
        async {
            // Owner: takes cache ownership first, then needs the project lock the other thread holds.
            start {
                workerLeaseService.runAsWorkerThread {
                    thread.blockUntil.otherHoldsProjectLock
                    coordinator.useCache {
                        instant.ownsCache
                        def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
                        workerLeaseService.withLocks([projectLock]) {
                            instant.ownerGotProjectLock
                        }
                    }
                }
            }
            // Waiter: holds the project lock, then blocks for cache ownership while still inside withLocks.
            start {
                workerLeaseService.runAsWorkerThread {
                    def projectLock = workerLeaseService.getProjectLock(path("root"), path(":project"))
                    workerLeaseService.withLocks([projectLock]) {
                        instant.otherHoldsProjectLock
                        thread.blockUntil.ownsCache
                        coordinator.useCache {
                            instant.waiterEnteredCache
                        }
                    }
                }
            }
        }

        then:
        instant.ownerGotProjectLock != null
        instant.waiterEnteredCache != null
    }
}
