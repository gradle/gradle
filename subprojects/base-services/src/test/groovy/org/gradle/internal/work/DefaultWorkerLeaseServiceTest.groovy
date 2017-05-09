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

package org.gradle.internal.work

import org.gradle.api.Action
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.TestTrackedResourceLock
import spock.lang.Specification


class DefaultWorkerLeaseServiceTest extends Specification {
    def coordinationService = new DefaultResourceLockCoordinationService()
    def workerLeaseService = new DefaultWorkerLeaseService(coordinationService, true, 1)

    def "can use withLock to execute an action with resources locked"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock1", false)

        when:
        workerLeaseService.withLocks(lock1, lock2).execute {
            assert lock1.lockedState
            assert lock2.lockedState
            assert lock1.doIsLockedByCurrentThread()
            assert lock2.doIsLockedByCurrentThread()
            executed = true
        }

        then:
        executed

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    def "can use withoutProjectLock to execute an action with locks temporarily released"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock1", false)

        when:
        workerLeaseService.withLocks(lock1, lock2).execute {
            assert lock1.lockedState
            assert lock2.lockedState
            workerLeaseService.withoutLocks(lock1, lock2).execute {
                assert !lock1.lockedState
                assert !lock2.lockedState
                assert !lock1.doIsLockedByCurrentThread()
                assert !lock2.doIsLockedByCurrentThread()
                executed = true
            }
            assert lock1.lockedState
            assert lock2.lockedState
        }

        then:
        executed

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    def "throws an exception from withoutProjectLock when locks are not currently held"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock1", false)

        when:
        workerLeaseService.withLocks(lock1).execute {
            assert lock1.lockedState
            assert !lock2.lockedState
            workerLeaseService.withoutLocks(lock1, lock2).execute {
                executed = true
            }
            assert lock1.lockedState
            assert !lock2.lockedState
        }

        then:
        thrown(IllegalStateException)
        !executed

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    TestTrackedResourceLock resourceLock(String displayName, boolean locked, boolean hasLock=false) {
        return new TestTrackedResourceLock(displayName, coordinationService, Mock(Action), Mock(Action), locked, hasLock)
    }

    TestTrackedResourceLock resourceLock(String displayName) {
        return resourceLock(displayName, false)
    }
}
