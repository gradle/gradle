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

import com.google.common.collect.ArrayListMultimap
import org.gradle.api.Transformer
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

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
                    return ResourceLockState.Disposition.FINISHED
                } else {
                    return ResourceLockState.Disposition.FAILED
                }
            }
        })

        then:
        lock1.lockedState == lock1Locked || (!lock1Locked && !lock2Locked)
        lock2.lockedState == lock2Locked || (!lock1Locked && !lock2Locked)
        result == (lock1.doHasResourceLock() && lock2.doHasResourceLock())

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
                                return ResourceLockState.Disposition.FINISHED
                            } else {
                                println "failed to acquire locks - blocking until state change"
                                return ResourceLockState.Disposition.RETRY
                            }
                        } finally {
                            count++
                            instant."executed${count}"
                        }
                    }
                })
                assert lock1.doHasResourceLock()
                assert lock2.doHasResourceLock()
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
                    return ResourceLockState.Disposition.FINISHED
                } else {
                    return ResourceLockState.Disposition.FAILED
                }
            }
        }
        def outerAction = new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState workerLeaseState) {
                if (lock[0].tryLock()) {
                    coordinationService.withStateLock(innerAction)
                    if (lock[1].tryLock()) {
                        return ResourceLockState.Disposition.FINISHED
                    }
                }

                // else
                return ResourceLockState.Disposition.FAILED
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
                return ResourceLockState.Disposition.FINISHED
            }
        })

        then:
        noExceptionThrown()
    }

    def "throws exception when current worker lease is requested outside of withStateLock"() {
        when:
        coordinationService.getCurrent()

        then:
        thrown(IllegalStateException)
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

                    return ResourceLockState.Disposition.FINISHED
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

    TestTrackedResourceLock resourceLock(String displayName, boolean locked) {
        return new TestTrackedResourceLock(displayName, ArrayListMultimap.create(), coordinationService, locked)
    }

    TestTrackedResourceLock resourceLock(String displayName) {
        return resourceLock(displayName, false)
    }
}
