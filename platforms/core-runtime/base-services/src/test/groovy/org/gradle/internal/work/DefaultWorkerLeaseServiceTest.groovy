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

import org.gradle.internal.Factory

class DefaultWorkerLeaseServiceTest extends AbstractWorkerLeaseServiceTest {
    def workerLeaseService = workerLeaseService()

    def "can use withLocks to execute a runnable with resources locked"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock1", false)

        when:
        workerLeaseService.withLocks([lock1, lock2], runnable {
            assert lock1.lockedState
            assert lock2.lockedState
            assert lock1.doIsLockedByCurrentThread()
            assert lock2.doIsLockedByCurrentThread()
            executed = true
        })

        then:
        executed

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    def "can use withLocks to execute a callable with resources locked"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock2", false)

        when:
        executed = workerLeaseService.withLocks([lock1, lock2], factory {
            assert lock1.lockedState
            assert lock2.lockedState
            assert lock1.doIsLockedByCurrentThread()
            assert lock2.doIsLockedByCurrentThread()
            return true
        })

        then:
        executed

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    def "can use withoutLocks to execute a runnable with locks temporarily released"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock2", false)

        when:
        workerLeaseService.withLocks([lock1, lock2]) {
            assert lock1.lockedState
            assert lock2.lockedState
            workerLeaseService.withoutLocks([lock1, lock2], runnable {
                assert !lock1.lockedState
                assert !lock2.lockedState
                assert !lock1.doIsLockedByCurrentThread()
                assert !lock2.doIsLockedByCurrentThread()
                executed = true
            })
            assert lock1.lockedState
            assert lock2.lockedState
        }

        then:
        executed

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    def "can use withoutLocks to execute a callable with locks temporarily released"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock2", false)

        when:
        workerLeaseService.withLocks([lock1, lock2]) {
            assert lock1.lockedState
            assert lock2.lockedState
            executed = workerLeaseService.withoutLocks([lock1, lock2], factory {
                assert !lock1.lockedState
                assert !lock2.lockedState
                assert !lock1.doIsLockedByCurrentThread()
                assert !lock2.doIsLockedByCurrentThread()
                return true
            })
            assert lock1.lockedState
            assert lock2.lockedState
        }

        then:
        executed

        and:
        !lock1.lockedState
        !lock2.lockedState
    }

    def "throws an exception from withoutLocks when locks are not currently held"() {
        boolean executed = false
        def lock1 = resourceLock("lock1", false)
        def lock2 = resourceLock("lock2", false)

        when:
        workerLeaseService.withLocks([lock1]) {
            assert lock1.lockedState
            assert !lock2.lockedState
            workerLeaseService.withoutLocks([lock1, lock2], runnable {
                executed = true
            })
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

    Runnable runnable(Closure closure) {
        return new Runnable() {
            @Override
            void run() {
                closure.run()
            }
        }
    }

    Factory factory(Closure closure) {
        return new Factory() {
            @Override
            Object create() {
                return closure.call()
            }
        }
    }
}
