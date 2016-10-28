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

import groovy.transform.NotYetImplemented
import org.gradle.api.Action
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.internal.Factory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile

import java.util.concurrent.locks.ReentrantLock

class LockOnDemandCrossProcessCacheAccessTest extends ConcurrentSpec {
    def file = new TestFile("some-file.lock")
    def lockManager = Mock(FileLockManager)
    def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), Stub(Action), Stub(Action))

    def "acquires lock then runs action and releases on completion"() {
        def action = Mock(Factory)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _) >> lock

        then:
        1 * action.create() >> "result"

        then:
        1 * lock.close()
        0 * _
    }

    def "releases lock on failure"() {
        def action = Mock(Factory)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()

        when:
        cacheAccess.withFileLock(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * lockManager.lock(file, _, _) >> lock

        then:
        1 * action.create() >> { throw failure }

        then:
        1 * lock.close()
        0 * _
    }

    def "a thread can nest actions"() {
        def action1 = Mock(Factory)
        def action2 = Mock(Factory)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action1)

        then:
        1 * lockManager.lock(file, _, _) >> lock
        1 * action1.create() >> { cacheAccess.withFileLock(action2) }
        1 * action2.create() >> "result"
        1 * lock.close()
        0 * _
    }

    def "lock is acquired once when multiple threads run actions concurrently"() {
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
        1 * lockManager.lock(file, _, _) >> lock
        1 * lock.close()
        0 * _
    }

    def "can acquire lock and release later"() {
        def lock = Mock(FileLock)

        when:
        def releaseAction = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _) >> lock
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
        1 * lockManager.lock(file, _, _) >> lock
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
        1 * lockManager.lock(file, _, _) >> lock
        1 * lock.close()
        0 * _
    }

    def "can release lock from different thread to the thread that acquired the lock"() {
        def lock = Mock(FileLock)
        def releaseAction

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
        1 * lockManager.lock(file, _, _) >> lock
        1 * lock.close()
        0 * _
    }

    def "initializes cache after lock is acquired"() {
        def action = Mock(Factory)
        def lock = Mock(FileLock)
        def initAction = Mock(CacheInitializationAction)
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), initAction, Stub(Action), Stub(Action))

        when:
        cacheAccess.open()

        then:
        0 *_

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock)

        then:
        1 * action.create() >> "result"

        then:
        1 * lock.close()
        0 * _

        when:
        def release = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _) >> lock

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
        def action = Mock(Factory)
        def lock = Mock(FileLock)
        def failure = new RuntimeException()
        def initAction = Mock(CacheInitializationAction)
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), initAction, Stub(Action), Stub(Action))

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
        1 * lockManager.lock(file, _, _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock) >> { throw failure }

        then:
        1 * lock.close()
        0 * _
    }

    def "can provide an action to run after the lock is released"() {
        def lock = Mock(FileLock)
        def completion = Mock(Runnable)

        when:
        def releaseAction = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _) >> lock
        0 * _

        when:
        releaseAction.run()

        then:
        1 * lock.close()

        then:
        completion.run()
        0 * _
    }

    def "runs completion action even when unlock fails"() {
        def lock = Mock(FileLock)
        def completion = Mock(Runnable)
        def failure = new RuntimeException()

        when:
        def releaseAction = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _) >> lock
        0 * _

        when:
        releaseAction.run()

        then:
        1 * lock.close() >> { throw failure }

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        completion.run()
        0 * _
    }

    def "notifies handler when lock is acquired and released"() {
        def action = Mock(Factory)
        def onOpen = Mock(Action)
        def onClose = Mock(Action)
        def lock = Mock(FileLock)
        def cacheAccess = new LockOnDemandCrossProcessCacheAccess("<cache>", file, LockOptionsBuilder.mode(FileLockManager.LockMode.Exclusive), lockManager, new ReentrantLock(), Stub(CacheInitializationAction), onOpen, onClose)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _) >> lock

        then:
        1 * onOpen.execute(lock)

        then:
        action.create() >> "result"

        then:
        1 * onClose.execute(lock)

        then:
        1 * lock.close()
        0 * _

        when:
        def release1 = cacheAccess.acquireFileLock()

        then:
        1 * lockManager.lock(file, _, _) >> lock
        1 * onOpen.execute(lock)
        0 * _

        when:
        def release2 = cacheAccess.acquireFileLock()
        release1.run()

        then:
        0 * _

        when:
        release2.run()

        then:
        1 * onClose.execute(lock)
        1 * lock.close()
        0 * _
    }

    @NotYetImplemented
    def "open handler can acquire and release lock"() {
        expect: false
    }

    @NotYetImplemented
    def "close handler can acquire and release lock"() {
        expect: false
    }

    @NotYetImplemented
    def "releases lock when open handler fails"() {
        expect: false
    }

    @NotYetImplemented
    def "releases lock when close handler fails"() {
        expect: false
    }

    @NotYetImplemented
    def "can acquire lock after previous acquire fails"() {
        expect: false
    }

    @NotYetImplemented
    def "cannot close while holding the lock"() {
        def action = Mock(Factory)
        def lock = Mock(FileLock)

        when:
        cacheAccess.withFileLock(action)

        then:
        1 * lockManager.lock(file, _, _) >> lock

        then:
        1 * action.create() >> {
            cacheAccess.close()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot close cache access for <cache> as it is currently in use by 1 threads.'
        1 * lock.close()
        0 * _
    }

    @NotYetImplemented
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
        e.message == 'Cannot close cache access for <cache> as it is currently in use by 1 threads.'

        and:
        1 * lockManager.lock(file, _, _) >> lock
        1 * lock.close()
        0 * _
    }

    @NotYetImplemented
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
        e.message == 'Cannot close cache access for <cache> as it is currently in use by 1 threads.'

        and:
        1 * lockManager.lock(file, _, _) >> lock
        1 * lock.close()
        0 * _
    }

    @NotYetImplemented
    def "cannot run action when close has started"() {
        expect: false
    }

    @NotYetImplemented
    def "cannot acquire lock when close has started"() {
        expect: false
    }
}
