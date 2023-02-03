/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.resources


import org.gradle.internal.InternalTransformer
import org.gradle.internal.MutableBoolean
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.lock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.tryLock
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock

class SharedResourceLeaseRegistryTest extends ConcurrentSpec {
    def coordinationService = new DefaultResourceLockCoordinationService()
    def sharedResourceLeaseRegistry = new SharedResourceLeaseRegistry(coordinationService)

    def "can cleanly lock and unlock a shared resource"() {
        given:
        sharedResourceLeaseRegistry.registerSharedResource('resource', 1)

        and:
        def sharedResourceLock = sharedResourceLeaseRegistry.getResourceLock('resource')
        assert !lockIsHeld(sharedResourceLock)

        when:
        def success = coordinationService.withStateLock(tryLock(sharedResourceLock))

        then:
        success
        lockIsHeld(sharedResourceLock)

        when:
        success = coordinationService.withStateLock(unlock(sharedResourceLock))

        then:
        success
        !lockIsHeld(sharedResourceLock)
    }

    def "multiple threads can coordinate locking of a shared resource"() {
        given:
        sharedResourceLeaseRegistry.registerSharedResource('resource', 1)
        def threadCount = 10
        def started = new CountDownLatch(threadCount)

        when:
        async {
            threadCount.times {
                start {
                    started.countDown()
                    thread.blockUntil.releaseAll
                    def sharedResourceLock = sharedResourceLeaseRegistry.getResourceLock('resource')
                    coordinationService.withStateLock(lock(sharedResourceLock))
                    assert lockIsHeld(sharedResourceLock)
                    coordinationService.withStateLock(unlock(sharedResourceLock))
                }
            }
            started.await()
            instant.releaseAll
        }

        then:
        noExceptionThrown()
    }

    def "locks on different shared resources can be concurrently held"() {
        given:
        sharedResourceLeaseRegistry.registerSharedResource('resource-a', 1)
        sharedResourceLeaseRegistry.registerSharedResource('resource-b', 1)

        and:
        def lockA = sharedResourceLeaseRegistry.getResourceLock('resource-a')
        def lockB = sharedResourceLeaseRegistry.getResourceLock('resource-b')

        when:
        coordinationService.withStateLock(lock(lockA))
        coordinationService.withStateLock(lock(lockB))

        then:
        lockIsHeld(lockA)
        lockIsHeld(lockB)

        when:
        coordinationService.withStateLock(unlock(lockA))
        coordinationService.withStateLock(unlock(lockB))

        then:
        !lockIsHeld(lockA)
        !lockIsHeld(lockB)
    }

    def "multiple threads can hold leases from a shared resource"() {
        given:
        def numberOfThreads = 10
        sharedResourceLeaseRegistry.registerSharedResource('resource', numberOfThreads)
        def acquired = new CountDownLatch(numberOfThreads)

        when:
        async {
            numberOfThreads.times {
                start {
                    def sharedResourceLock = sharedResourceLeaseRegistry.getResourceLock('resource')
                    coordinationService.withStateLock(lock(sharedResourceLock))
                    assert lockIsHeld(sharedResourceLock)
                    acquired.countDown()
                    acquired.await()
                    coordinationService.withStateLock(unlock(sharedResourceLock))
                }
            }
        }

        then:
        noExceptionThrown()
    }

    boolean lockIsHeld(final ResourceLock resourceLock) {
        MutableBoolean held = new MutableBoolean()
        coordinationService.withStateLock(new InternalTransformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                held.set(resourceLock.locked && resourceLock.isLockedByCurrentThread())
                return ResourceLockState.Disposition.FINISHED
            }
        })
        return held.get()
    }
}
