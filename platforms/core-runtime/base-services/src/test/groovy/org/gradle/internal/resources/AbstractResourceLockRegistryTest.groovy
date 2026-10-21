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


import spock.lang.Specification

class AbstractResourceLockRegistryTest extends Specification {
    def coordinationService = Stub(ResourceLockCoordinationService)

    def "can get a lock associated with the current thread"() {
        when:
        def registry = new MultiLockTestRegistry(coordinationService)
        def lock = registry.getResourceLock("test")

        then:
        lock instanceof TestTrackedResourceLock

        when:
        lock.tryLock()

        then:
        registry.getResourceLocksByCurrentThread() == [lock]
    }

    def "does not get locks associated with other threads"() {
        when:
        def registry = new MultiLockTestRegistry(coordinationService)
        def lock = registry.getResourceLock("test")
        inNewThread { registry.getResourceLock("another").tryLock() }

        and:
        lock.tryLock()

        then:
        registry.getResourceLocksByCurrentThread() == [lock]
    }

    def "identifies open locks in the registry"() {
        when:
        def registry = new MultiLockTestRegistry(coordinationService)
        def lock = registry.getResourceLock("test")

        then:
        !registry.hasOpenLocks()

        when:
        lock.lockedState = true

        then:
        registry.hasOpenLocks()
    }

    def "a thread can hold several locks of a registry that allows multiple locks"() {
        given:
        def registry = new MultiLockTestRegistry(coordinationService)
        def lock = registry.getResourceLock("test")
        def other = registry.getResourceLock("other")

        when:
        lock.tryLock()
        other.tryLock()

        then:
        registry.getResourceLocksByCurrentThread() as Set == [lock, other] as Set
    }

    def "a thread cannot hold more than one lock of a registry that allows a single lock"() {
        given:
        def singleLockRegistry = new SingleLockTestRegistry(coordinationService)
        def lock = singleLockRegistry.getResourceLock("test")
        def other = singleLockRegistry.getResourceLock("other")

        when:
        lock.tryLock()

        then:
        singleLockRegistry.getResourceLocksByCurrentThread() == [lock]

        when:
        other.tryLock()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot acquire other. The current thread already holds test and may hold only one lock of this kind at a time."

        and: "the failed acquisition leaves no trace"
        singleLockRegistry.getResourceLocksByCurrentThread() == [lock]
        !other.lockedState
    }

    def "a single lock registry accepts another lock once the first is released"() {
        given:
        def singleLockRegistry = new SingleLockTestRegistry(coordinationService)
        def lock = singleLockRegistry.getResourceLock("test")
        def other = singleLockRegistry.getResourceLock("other")

        when:
        lock.tryLock()
        lock.unlock()
        other.tryLock()

        then:
        singleLockRegistry.getResourceLocksByCurrentThread() == [other]
    }

    def "a single lock registry exposes the lock held by the current thread"() {
        given:
        def singleLockRegistry = new SingleLockTestRegistry(coordinationService)
        def lock = singleLockRegistry.getResourceLock("test")

        expect:
        singleLockRegistry.getLockForCurrentThread() == null

        when:
        lock.tryLock()

        then:
        singleLockRegistry.getLockForCurrentThread() == lock

        when:
        lock.unlock()

        then:
        singleLockRegistry.getLockForCurrentThread() == null
    }

    def inNewThread(Closure closure) {
        def thread = new Thread(closure)
        thread.start()
        thread.join()
    }

    static class MultiLockTestRegistry extends MultiLockRegistry<String, ResourceLock> {
        MultiLockTestRegistry(ResourceLockCoordinationService coordinationService) {
            super(coordinationService)
        }

        def getResourceLock(String displayName) {
            return getOrRegisterResourceLock(displayName, new AbstractResourceLockRegistry.ResourceLockProducer<String, ResourceLock>() {
                @Override
                ResourceLock create(String name, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
                    return new TestTrackedResourceLock(name, coordinationService, owner)
                }
            })
        }
    }

    static class SingleLockTestRegistry extends SingleLockRegistry<String, ResourceLock> {
        SingleLockTestRegistry(ResourceLockCoordinationService coordinationService) {
            super(coordinationService)
        }

        def getResourceLock(String displayName) {
            return getOrRegisterResourceLock(displayName, new AbstractResourceLockRegistry.ResourceLockProducer<String, ResourceLock>() {
                @Override
                ResourceLock create(String name, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
                    return new TestTrackedResourceLock(name, coordinationService, owner)
                }
            })
        }
    }
}
