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
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.*

class ExclusiveAccessResourceLockTest extends ConcurrentSpec {
    def coordinationService = new DefaultResourceLockCoordinationService()
    def resourceLock = new ExclusiveAccessResourceLock("test", coordinationService, Mock(Action), Mock(Action))

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
        coordinationService.withStateLock(tryLock(resourceLock))

        then:
        resourceLock.doIsLocked()

        when:
        coordinationService.withStateLock(unlock(resourceLock))

        then:
        !resourceLock.doIsLocked()
    }

    def "can lock a resource that is already locked"() {
        given:
        assert coordinationService.withStateLock(tryLock(resourceLock))
        assert resourceLock.doIsLocked()

        when:
        coordinationService.withStateLock(tryLock(resourceLock))

        then:
        noExceptionThrown()
        resourceLock.doIsLocked()
    }

    def "can unlock a resource that is already unlocked"() {
        when:
        coordinationService.withStateLock(unlock(resourceLock))

        then:
        noExceptionThrown()
        !resourceLock.doIsLocked()
    }

    def "cannot lock a resource that is already locked by another thread"() {
        when:
        async {
            coordinationService.withStateLock(tryLock(resourceLock))

            start {
                assert !coordinationService.withStateLock(tryLock(resourceLock))
                assert resourceLock.owner != Thread.currentThread()
                instant.child1Finished
            }

            thread.blockUntil.child1Finished
            coordinationService.withStateLock(unlock(resourceLock))

            start {
                assert coordinationService.withStateLock(tryLock(resourceLock))
                assert resourceLock.owner == Thread.currentThread()
            }
        }

        then:
        noExceptionThrown()
    }

    def "can unlock a lock acquired on behalf of another thread"() {
        Thread otherThread = new Thread({
            assert resourceLock.owner == Thread.currentThread()
        })

        when:
        assert coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (resourceLock.tryLock(otherThread)) {
                    return ResourceLockState.Disposition.FINISHED
                } else {
                    return ResourceLockState.Disposition.FAILED
                }
            }
        })

        then:
        resourceLock.owner == otherThread

        when:
        otherThread.start()
        otherThread.join()

        then:
        noExceptionThrown()

        when:
        coordinationService.withStateLock(unlock(resourceLock))

        then:
        resourceLock.owner == null
    }

    def "cannot unlock a lock acquired by another thread"() {
        Thread lockingThread = new Thread({
            assert coordinationService.withStateLock(tryLock(resourceLock))
        })
        Thread otherThread = new Thread({
            coordinationService.withStateLock(unlock(resourceLock))
        })

        when:
        lockingThread.start()
        lockingThread.join()

        then:
        resourceLock.owner == lockingThread

        when:
        otherThread.start()
        otherThread.join()

        then:
        resourceLock.owner == lockingThread
    }
}
