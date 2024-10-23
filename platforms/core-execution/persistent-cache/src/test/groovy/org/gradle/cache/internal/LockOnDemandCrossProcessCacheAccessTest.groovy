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
import org.gradle.cache.FileLockReleasedSignal
import org.gradle.cache.LockOptions
import org.gradle.cache.internal.filelock.DefaultLockOptions
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile

import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import java.util.function.Supplier

class LockOnDemandCrossProcessCacheAccessTest extends ConcurrentSpec {
    def file = new TestFile("some-file.lock")
    def lockManager = Mock(FileLockManager)
    def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), Stub(Consumer), Stub(Consumer))

    def "close when lock has never been acquired"() {
        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "acquires lock then runs action and retains lock on completion"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> lock

        then:
        1 * action.get() >> "result"

        then:
        0 * _
    }

    def "releases retained lock when no actions running on contention"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction

        given:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }

        cacheAccess.withFileLock(action)

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "releases retained lock at completion of action on contention"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }
        1 * action.get() >> { contendedAction.accept(signal); "result" }
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "releases retained lock on close"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)

        given:
        _ * lockManager.lock(file, _, _, _, _) >> lock
        _ * lockManager.allowContention(lock, _)

        cacheAccess.withFileLock(action)

        when:
        cacheAccess.close()

        then:
        1 * lock.close()
        0 * _
    }

    def "releases lock after failed action"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        def failure = new RuntimeException()
        Consumer contendedAction

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
            return lock
        }

        then:
        1 * action.get() >> { throw failure }

        then:
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "lock is acquired once when a thread nests actions"() {
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction

        when:
        cacheAccess.withFileLock(action1)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }

        1 * action1.get() >> { cacheAccess.withFileLock(action2) }
        1 * action2.get() >> "result"
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "lock is acquired when multiple threads run actions concurrently"() {
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction

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
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "can acquire lock and release later"() {
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction

        when:
        def releaseAction = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }
        0 * _

        when:
        releaseAction.run()

        then:
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "thread can acquire lock multiple times"() {
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction

        when:
        def releaseAction = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }
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
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "multiple threads can acquire lock concurrently"() {
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction

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
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "can release lock from different thread to the thread that acquired the lock"() {
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Runnable releaseAction
        Consumer contendedAction

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
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "initializes cache after lock is acquired"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        def initAction = Mock(CacheInitializationAction)
        Consumer contendedAction
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), initAction, Stub(Consumer), Stub(Consumer))

        when:
        cacheAccess.open()

        then:
        0 *_

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock)

        then:
        1 * action.get() >> { contendedAction.accept(signal) }

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _

        when:
        def release = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock)
        0 * _

        when:
        release.run()

        then:
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * lock.close()
        1 * signal.trigger()
        0 * _
    }

    def "releases file lock when init action fails"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()
        def initAction = Mock(CacheInitializationAction)
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), initAction, Stub(Consumer), Stub(Consumer))

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
        1 * lockManager.lock(file, _, _, _, _) >> lock

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
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }

        then:
        1 * onOpen.accept(lock)

        then:
        1 * action.get() >> "result"
        0 * _

        when:
        def release1 = cacheAccess.acquireFileLock()

        then:
        0 * _

        when:
        def release2 = cacheAccess.acquireFileLock()
        release1.run()
        contendedAction.accept(signal)

        then:
        0 * _

        when:
        release2.run()

        then:
        1 * onClose.accept(lock)
        1 * lock.close()
        1 * signal.trigger()
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
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> lock

        then:
        1 * onOpen.accept(lock)

        then:
        1 * action.get() >> "result"
        0 * _

        when:
        cacheAccess.close()

        then:
        1 * onClose.accept(lock)

        then:
        1 * lock.close()
        0 * _
    }

    def "notifies handler when lock released on contention"() {
        def action = Mock(Supplier)
        def onOpen = Mock(Consumer)
        def onClose = Mock(Consumer)
        def lock = Mock(FileLock)
        def signal = Mock(FileLockReleasedSignal)
        Consumer contendedAction
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }

        then:
        1 * onOpen.accept(lock)

        then:
        1 * action.get() >> "result"
        0 * _

        when:
        contendedAction.accept(signal)

        then:
        1 * onClose.accept(lock)
        1 * lock.close()
        1 * signal.trigger()
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
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * lockManager.lock(file, _, _, _, _) >> lock

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
        Consumer contendedAction
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }

        then:
        1 * onOpen.accept(lock)

        then:
        1 * action.get() >> "result"
        0 * _

        when:
        contendedAction.accept({} as FileLockReleasedSignal)

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
        Consumer contendedAction

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        then:
        1 * lockManager.lock(file, _, _, _, _) >> { throw failure }
        0 * _

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Consumer<FileLockReleasedSignal> whenContended -> contendedAction = whenContended
                return lock
        }
        1 * action.get() >> "result"
        0 * _

        when:
        cacheAccess.close()

        then:
        1 * lock.close()
        0 * _
    }

    def "cannot close while holding the lock"() {
        def action = Mock(Supplier)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _, _, _) >> lock

        then:
        1 * action.get() >> {
            cacheAccess.close()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot close cache access for <cache> as it is currently in use for 1 operations.'
        0 * _

        when:
        cacheAccess.close()

        then:
        1 * lock.close()
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
        1 * lockManager.lock(file, _, _, _, _) >> lock
        0 * _

        when:
        cacheAccess.close()

        then:
        1 * lock.close()
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
        1 * lockManager.lock(file, _, _, _, _) >> lock
        0 * _

        when:
        cacheAccess.close()

        then:
        1 * lock.close()
        0 * _
    }
}
