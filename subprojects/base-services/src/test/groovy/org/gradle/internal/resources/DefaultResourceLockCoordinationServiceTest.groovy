/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import static org.gradle.internal.resources.ResourceLockState.Disposition.*
import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.*

class DefaultResourceLockCoordinationServiceTest extends ConcurrentSpec {
    def coordinationService = new DefaultResourceLockCoordinationService()

    def "can acquire locks atomically using withStateLock"() {
        def lock1 = resourceLock("lock1", lock1Locked)
        def lock2 = resourceLock("lock2", lock2Locked)

        when:
        def result = coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState workerLeaseState) {
                if (lock1.tryLock() && lock2.tryLock()) {
                    return FINISHED
                } else {
                    return FAILED
                }
            }
        })

        then:
        lock1.lockedState == lock1Locked || (!lock1Locked && !lock2Locked)
        lock2.lockedState == lock2Locked || (!lock1Locked && !lock2Locked)
        result == (lock1.doIsLockedByCurrentThread() && lock2.doIsLockedByCurrentThread())

        where:
        lock1Locked | lock2Locked
        true        | true
        true        | false
        false       | true
        false       | false
    }

    def "can retry a lock action when locks are released"() {
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock2", true)
        def count = 0

        when:
        async {
            start {
                coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                    @Override
                    ResourceLockState.Disposition transform(ResourceLockState workerLeaseState) {
                        try {
                            if (lock1.tryLock() && lock2.tryLock()) {
                                return FINISHED
                            } else {
                                println "failed to acquire locks - blocking until state change"
                                return RETRY
                            }
                        } finally {
                            count++
                            instant."executed${count}"
                        }
                    }
                })
                assert lock1.doIsLockedByCurrentThread()
                assert lock2.doIsLockedByCurrentThread()
            }

            thread.blockUntil.executed1

            ConcurrentTestUtil.poll {
                assert !lock1.lockedState
                assert lock2.lockedState
            }

            lock2.lockedState = false
            coordinationService.notifyStateChange()

            thread.blockUntil.executed2
        }

        then:
        lock1.lockedState
        lock2.lockedState
    }

    def "can nest multiple calls to withStateLock"() {
        def lock = [
            resourceLock("lock1"),
            resourceLock("lock2"),
            resourceLock("lock3"),
            resourceLock("lock4")
        ]

        given:
        def innerAction = new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState workerLeaseState) {
                if (lock[2].tryLock() && lock[3].tryLock()) {
                    return FINISHED
                } else {
                    return FAILED
                }
            }
        }
        def outerAction = new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState workerLeaseState) {
                if (lock[0].tryLock()) {
                    coordinationService.withStateLock(innerAction)
                    if (lock[1].tryLock()) {
                        return FINISHED
                    }
                }

                // else
                return FAILED
            }
        }

        when:
        beforeState.eachWithIndex{ boolean locked, int i -> lock[i].lockedState = locked }
        coordinationService.withStateLock(outerAction)

        then:
        afterState.eachWithIndex{ boolean locked, int i -> assert lock[i].lockedState == locked }

        where:
        beforeState                  | afterState
        [false, false, true, false]  | [true, true, true, false]
        [true, false, true, false]   | [true, false, true, false]
        [false, true, false, true]   | [false, true, false, true]
        [true, false, false, false]  | [true, false, false, false]
        [false, false, false, false] | [true, true, true, true]
        [false, true, false, false]  | [false, true, true, true]
    }

    def "can get the current worker lease state"() {
        when:
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState workerLeaseState) {
                assert coordinationService.getCurrent() == workerLeaseState
                return FINISHED
            }
        })

        then:
        noExceptionThrown()
    }

    def "locks are rolled back when an exception is thrown"() {
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock2", false)

        when:
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState workerLeaseState) {
                try {
                    lock1.tryLock()
                    lock2.tryLock()

                    assert lock1.lockedState
                    assert lock2.lockedState

                    return FINISHED
                } finally {
                    throw new RuntimeException("BOOM!")
                }
            }
        })

        then:
        thrown(RuntimeException)

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    def "can lock resources atomically with tryLock"() {
        def lock1 = resourceLock("lock1", lock1Locked)
        def lock2 = resourceLock("lock1", lock2Locked)

        when:
        def disposition = null
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                disposition = tryLock(lock1, lock2).transform(resourceLockState)
                return FINISHED
            }
        })

        then:
        disposition == expectedDisposition

        and:
        lock1.doIsLockedByCurrentThread() == !lock1Locked
        lock2.doIsLockedByCurrentThread() == (!lock1Locked && !lock2Locked)

        where:
        lock1Locked | lock2Locked | expectedDisposition
        true        | true        | FAILED
        true        | false       | FAILED
        false       | true        | FAILED
        false       | false       | FINISHED
    }

    def "can block on locked resources with lock"() {
        def lock1 = resourceLock("lock1", lock1Locked)
        def lock2 = resourceLock("lock1", lock2Locked)

        when:
        def disposition = null
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                disposition = lock(lock1, lock2).transform(resourceLockState)
                return FINISHED
            }
        })

        then:
        disposition == expectedDisposition

        and:
        lock1.doIsLockedByCurrentThread() == !lock1Locked
        lock2.doIsLockedByCurrentThread() == (!lock1Locked && !lock2Locked)
        where:
        lock1Locked | lock2Locked | expectedDisposition
        true        | true        | RETRY
        true        | false       | RETRY
        false       | true        | RETRY
        false       | false       | FINISHED
    }

    def "can unlock resources with unlock"() {
        def lock1 = resourceLock("lock1", lock1Locked, true)
        def lock2 = resourceLock("lock1", lock2Locked, true)

        when:
        def disposition = null
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                disposition = unlock(lock1, lock2).transform(resourceLockState)
                return FINISHED
            }
        })

        then:
        disposition == expectedDisposition

        and:
        !lock1.lockedState
        !lock2.lockedState

        where:
        lock1Locked | lock2Locked | expectedDisposition
        true        | true        | FINISHED
        true        | false       | FINISHED
        false       | true        | FINISHED
        false       | false       | FINISHED
    }

    TestTrackedResourceLock resourceLock(String displayName, boolean locked, boolean hasLock=false) {
        return new TestTrackedResourceLock(displayName, coordinationService, Mock(Action), Mock(Action), locked, hasLock)
    }

    TestTrackedResourceLock resourceLock(String displayName) {
        return resourceLock(displayName, false)
    }
}
