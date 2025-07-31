/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.filelock.DefaultLockOptions
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile

import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Supplier

class LockOnDemandEagerReleaseCrossProcessCacheAccessTest extends ConcurrentSpec {
    def file = new TestFile("some-file.lock")
    def lockManager = Mock(FileLockManager)
    def cacheAccess = newCacheAccess()

    private LockOnDemandEagerReleaseCrossProcessCacheAccess newCacheAccessWithInit(CacheInitializationAction action) {
        newCacheAccess(Stub(Consumer), Stub(Consumer), action)
    }

    private LockOnDemandEagerReleaseCrossProcessCacheAccess newCacheAccess(Consumer<FileLock> onOpen = null, Consumer<FileLock> onClose = null, CacheInitializationAction action = null) {
        new LockOnDemandEagerReleaseCrossProcessCacheAccess(
            "<cache>",
            file,
            DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive),
            lockManager,
            new ReentrantLock(),
            action ?: Stub(CacheInitializationAction),
            onOpen ?: Stub(Consumer),
            onClose ?: Stub(Consumer)
        )
    }

    def "close when lock has never been acquired"() {
        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "acquires lock then runs action and releases lock on completion"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * action.get() >> "result"

        then:
        1 * lock.close()

        then:
        0 * _
    }

    def "releases lock after failed action"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * action.get() >> { throw failure }

        then:
        1 * lock.close()
        0 * _
    }

    def "lock is acquired once when a thread nests actions"() {
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action1)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        1 * action1.get() >> { cacheAccess.withFileLock(action2) }
        1 * action2.get() >> "result"
        0 * _

        then:
        1 * lock.close()
        0 * _
    }

    def "lock is acquired when multiple threads run actions concurrently"() {
        def lock = Mock(FileLock)

        when:
        async {
            start {
                cacheAccess.withFileLock {
                    instant.action1
                    thread.blockUntil.action2
                }
            }
            start {
                cacheAccess.withFileLock {
                    instant.action2
                    thread.blockUntil.action1
                }
            }
        }

        then:
        1 * lockManager.lock(file, _, _, _) >> lock
        0 * _

        then:
        1 * lock.close()
        0 * _
    }

    def "can acquire lock and release later"() {
        def lock = Mock(FileLock)

        when:
        def releaseAction = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _, _) >> lock
        0 * _

        when:
        releaseAction.run()

        then:
        1 * lock.close()
        0 * _
    }

    def "thread can acquire lock multiple times"() {
        def lock = Mock(FileLock)

        when:
        def releaseAction = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _, _) >> lock
        0 * _

        when:
        def releaseAction2 = cacheAccess.acquireFileLock()
        cacheAccess.withFileLock {
            // nothing
        }
        releaseAction.run()

        then:
        0 * _

        when:
        releaseAction2.run()

        then:
        1 * lock.close()
        0 * _
    }

    def "multiple threads can acquire lock concurrently"() {
        def lock = Mock(FileLock)

        when:
        async {
            start {
                def release1 = cacheAccess.acquireFileLock()
                thread.blockUntil.action2
                release1.run()
                instant.action1
            }
            start {
                def release2 = cacheAccess.acquireFileLock()
                instant.action2
                thread.blockUntil.action1
                release2.run()
            }
        }

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        1 * lock.close()
        0 * _
    }

    def "can release lock from different thread to the thread that acquired the lock"() {
        def lock = Mock(FileLock)
        Runnable releaseAction

        when:
        async {
            start {
                releaseAction = cacheAccess.acquireFileLock()
                instant.action1
            }
            start {
                thread.blockUntil.action1
                releaseAction.run()
            }
        }

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        1 * lock.close()
        0 * _
    }

    def "initializes cache after lock is acquired"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def initAction = Mock(CacheInitializationAction)
        def cacheAccess = newCacheAccessWithInit(initAction)

        when:
        cacheAccess.open()

        then:
        0 *_

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock)

        then:
        1 * action.get()

        then:
        1 * lock.close()
        0 * _

        when:
        def release = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock)
        0 * _

        when:
        release.run()

        then:
        1 * lock.close()
        0 * _
    }

    def "releases file lock when init action fails"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()
        def initAction = Mock(CacheInitializationAction)
        def cacheAccess = newCacheAccessWithInit(initAction)

        when:
        cacheAccess.open()

        then:
        0 *_

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock) >> { throw failure }

        then:
        1 * lock.close()
        0 * _
    }

    def "notifies handler when lock is acquired and released"() {
        def action = Mock(Supplier)
        def onOpen = Mock(Consumer)
        def onClose = Mock(Consumer)
        def lock = Mock(FileLock)
        def nextLock = Mock(FileLock)

        def cacheAccess = newCacheAccess(onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * onOpen.accept(lock)

        then:
        1 * action.get() >> "result"

        then:
        1 * onClose.accept(lock)

        then:
        1 * lock.close()
        0 * _

        when:
        def release1 = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _, _) >> nextLock
        1 * onOpen.accept(nextLock)

        when:
        def release2 = cacheAccess.acquireFileLock()
        release1.run()

        then:
        0 * _

        when:
        release2.run()

        then:
        1 * onClose.accept(nextLock)

        then:
        1 * nextLock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "notifies handler when lock released on close"() {
        def action = Mock(Supplier)
        def onOpen = Mock(Consumer)
        def onClose = Mock(Consumer)
        def lock = Mock(FileLock)
        def cacheAccess = newCacheAccess(onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * onOpen.accept(lock)

        then:
        1 * action.get() >> "result"

        then:
        1 * onClose.accept(lock)

        then:
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "releases lock when acquire handler fails"() {
        def action = Mock(Supplier)
        def onOpen = Mock(Consumer)
        def onClose = Mock(Consumer)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()
        def cacheAccess = newCacheAccess(onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * onOpen.accept(lock) >> { throw failure }

        then:
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "releases lock when release handler fails"() {
        def action = Mock(Supplier)
        def onOpen = Mock(Consumer)
        def onClose = Mock(Consumer)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()

        def cacheAccess = newCacheAccess(onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * onOpen.accept(lock)

        then:
        1 * action.get() >> "result"

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * onClose.accept(lock) >> { throw failure }
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "can acquire lock after previous acquire fails"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        then:
        1 * lockManager.lock(file, _, _, _) >> { throw failure }
        0 * _

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock
        1 * action.get() >> "result"

        then:
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "cannot close while holding the lock"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * action.get() >> {
            cacheAccess.close()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot close cache access for <cache> as it is currently in use for 1 operations.'
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "close fails when action is currently running in another thread"() {
        def lock = Mock(FileLock)

        when:
        async {
            start {
                cacheAccess.withFileLock {
                    instant.acquired
                    thread.blockUntil.closed
                }
            }
            start {
                thread.blockUntil.acquired
                try {
                    cacheAccess.close()
                } finally {
                    instant.closed
                }
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot close cache access for <cache> as it is currently in use for 1 operations.'

        and:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "close fails when lock is currently held by another thread"() {
        def lock = Mock(FileLock)

        when:
        async {
            start {
                def release = cacheAccess.acquireFileLock()
                instant.acquired
                thread.blockUntil.closed
                release.run()
            }
            start {
                thread.blockUntil.acquired
                try {
                    cacheAccess.close()
                } finally {
                    instant.closed
                }
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot close cache access for <cache> as it is currently in use for 1 operations.'

        and:
        1 * lockManager.lock(file, _, _, _) >> lock

        then:
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }
}
