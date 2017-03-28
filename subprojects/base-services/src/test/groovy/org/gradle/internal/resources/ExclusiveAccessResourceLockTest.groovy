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
import com.google.common.collect.Multimap
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class ExclusiveAccessResourceLockTest extends ConcurrentSpec {
    def coordinationService = new DefaultResourceLockCoordinationService()
    Multimap<Long, ResourceLock> threads = ArrayListMultimap.create()
    def resourceLock = new ExclusiveAccessResourceLock("test", threads, coordinationService)

    def "throws an exception when not called from coordination service"() {
        when:
        resourceLock.tryLock()

        then:
        thrown(IllegalStateException)

        when:
        resourceLock.unlock()

        then:
        thrown(IllegalStateException)
    }

    def "can lock and unlock a resource"() {
        when:
        coordinationService.withStateLock(ResourceLockOperations.tryLock(resourceLock))

        then:
        resourceLock.doIsLocked()
        resourceLock.doHasResourceLock()

        when:
        coordinationService.withStateLock(ResourceLockOperations.unlock(resourceLock))

        then:
        !resourceLock.doIsLocked()
        !resourceLock.doHasResourceLock()
    }

    def "can lock a resource that is already locked"() {
        given:
        assert coordinationService.withStateLock(ResourceLockOperations.tryLock(resourceLock))
        assert resourceLock.doIsLocked()

        when:
        coordinationService.withStateLock(ResourceLockOperations.tryLock(resourceLock))

        then:
        noExceptionThrown()
        resourceLock.doIsLocked()
        resourceLock.doHasResourceLock()
    }

    def "can unlock a resource that is already unlocked"() {
        when:
        coordinationService.withStateLock(ResourceLockOperations.unlock(resourceLock))

        then:
        noExceptionThrown()
        !resourceLock.doIsLocked()
        !resourceLock.doHasResourceLock()
    }

    def "cannot lock a resource that is already locked by another thread"() {
        when:
        async {
            coordinationService.withStateLock(ResourceLockOperations.tryLock(resourceLock))

            start {
                assert !coordinationService.withStateLock(ResourceLockOperations.tryLock(resourceLock))
                assert !resourceLock.doHasResourceLock()
                instant.child1Finished
            }

            thread.blockUntil.child1Finished
            coordinationService.withStateLock(ResourceLockOperations.unlock(resourceLock))

            start {
                assert coordinationService.withStateLock(ResourceLockOperations.tryLock(resourceLock))
                assert resourceLock.doHasResourceLock()
            }
        }

        then:
        noExceptionThrown()
    }
}
