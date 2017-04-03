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
import spock.lang.Specification

import static org.gradle.internal.resources.ResourceLockOperations.*
import static org.gradle.internal.resources.ResourceLockState.Disposition.*


class ResourceLockOperationsTest extends Specification {
    def resourceLockState = Mock(ResourceLockState)
    def coordinationService = Mock(ResourceLockCoordinationService)

    def setup() {
        _ * coordinationService.current >> resourceLockState
    }

    def "can lock resources atomically with tryLock"() {
        def lock1 = resourceLock("lock1", lock1Locked)
        def lock2 = resourceLock("lock1", lock2Locked)

        when:
        def disposition = tryLock(lock1, lock2).transform(resourceLockState)

        then:
        disposition == expectedDisposition

        and:
        lock1.isLockedByCurrentThread() == !lock1Locked
        lock2.isLockedByCurrentThread() == (!lock1Locked && !lock2Locked)

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
        def disposition = lock(lock1, lock2).transform(resourceLockState)

        then:
        disposition == expectedDisposition

        and:
        lock1.isLockedByCurrentThread() == !lock1Locked
        lock2.isLockedByCurrentThread() == (!lock1Locked && !lock2Locked)

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
        def disposition = unlock(lock1, lock2).transform(resourceLockState)

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
}
