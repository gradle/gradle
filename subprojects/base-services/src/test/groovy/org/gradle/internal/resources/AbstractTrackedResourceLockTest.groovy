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
import spock.lang.Specification


class AbstractTrackedResourceLockTest extends Specification {
    def threadMap = ArrayListMultimap.create()
    def resourceLockState = Mock(ResourceLockState)
    def coordinationService = Mock(ResourceLockCoordinationService)
    def lock = new TestTrackedResourceLock("test", threadMap, coordinationService)

    def "tracks the lock in the thread map and current resource lock state"()  {
        given:
        assert !(lock in threadMap.get(Thread.currentThread().id))

        when:
        lock.tryLock()

        then:
        lock in threadMap.get(Thread.currentThread().id)

        and:
        2 * coordinationService.current >> resourceLockState
        1 * resourceLockState.registerLocked(lock)

        when:
        lock.unlock()

        then:
        !(lock in threadMap.get(Thread.currentThread().id))

        and:
        2 * coordinationService.current >> resourceLockState
    }

    def "checks that state methods are called inside coordination service transform"() {
        when:
        lock.hasResourceLock()

        then:
        1 * coordinationService.current

        when:
        lock.isLocked()

        then:
        1 * coordinationService.current
    }
}
