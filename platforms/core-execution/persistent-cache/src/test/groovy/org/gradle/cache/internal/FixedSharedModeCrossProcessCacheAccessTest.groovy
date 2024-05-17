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

import org.gradle.api.Action
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Specification

class FixedSharedModeCrossProcessCacheAccessTest extends Specification {
    def file = new TestFile("some-file.lock")
    def lockManager = Mock(FileLockManager)
    def initAction = Mock(CacheInitializationAction)
    def onOpenAction = Mock(Action)
    def onCloseAction = Mock(Action)
    def lockOptions = LockOptionsBuilder.mode(FileLockManager.LockMode.Shared)
    def cacheAccess = new FixedSharedModeCrossProcessCacheAccess("<cache>", file, lockOptions, lockManager, initAction, onOpenAction, onCloseAction)

    def "acquires lock then validates cache and runs handler action on open"() {
        def lock = Mock(FileLock)

        when:
        cacheAccess.open()

        then:
        1 * lockManager.lock(file, lockOptions, "<cache>") >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> false

        then:
        1 * onOpenAction.execute(lock)
        0 * _
    }

    def "acquires lock then initializes cache and runs handler action on open"() {
        def initialLock = Mock(FileLock)
        def exclusiveLock = Mock(FileLock)
        def sharedLock = Mock(FileLock)

        when:
        cacheAccess.open()

        then:
        1 * lockManager.lock(file, lockOptions, "<cache>") >> initialLock

        then:
        1 * initAction.requiresInitialization(initialLock) >> true
        1 * initialLock.close()

        then:
        1 * lockManager.lock(file, {it.mode == FileLockManager.LockMode.Exclusive }, "<cache>") >> exclusiveLock
        1 * initAction.requiresInitialization(exclusiveLock) >> true
        1 * exclusiveLock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(exclusiveLock)
        1 * exclusiveLock.close()

        then:
        1 * lockManager.lock(file, lockOptions, "<cache>") >> sharedLock
        1 * initAction.requiresInitialization(sharedLock) >> false

        then:
        1 * onOpenAction.execute(sharedLock)
        0 * _

        when:
        cacheAccess.close()

        then:
        1 * onCloseAction.execute(sharedLock)
        1 * sharedLock.close()
        0 * _
    }

    def "releases lock when cache initialization fails"() {
        def initialLock = Mock(FileLock)
        def exclusiveLock = Mock(FileLock)
        def failure = new RuntimeException()

        when:
        cacheAccess.open()

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * lockManager.lock(file, lockOptions, "<cache>") >> initialLock
        1 * initAction.requiresInitialization(initialLock) >> true
        1 * initialLock.close()

        then:
        1 * lockManager.lock(file, {it.mode == FileLockManager.LockMode.Exclusive}, "<cache>") >> exclusiveLock
        1 * initAction.requiresInitialization(exclusiveLock) >> true
        1 * exclusiveLock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(exclusiveLock) >> { throw failure }
        1 * exclusiveLock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "releases lock when handler fails"() {
        def initialLock = Mock(FileLock)
        def exclusiveLock = Mock(FileLock)
        def sharedLock = Mock(FileLock)
        def failure = new RuntimeException()

        when:
        cacheAccess.open()

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * lockManager.lock(file, lockOptions, "<cache>") >> initialLock
        1 * initAction.requiresInitialization(initialLock) >> true
        1 * initialLock.close()

        then:
        1 * lockManager.lock(file, {it.mode == FileLockManager.LockMode.Exclusive}, "<cache>") >> exclusiveLock
        1 * initAction.requiresInitialization(exclusiveLock) >> true
        1 * exclusiveLock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(exclusiveLock)
        1 * exclusiveLock.close()

        then:
        1 * lockManager.lock(file, lockOptions, "<cache>") >> sharedLock
        1 * initAction.requiresInitialization(sharedLock) >> false

        then:
        1 * onOpenAction.execute(sharedLock) >> { throw failure }
        1 * sharedLock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "runs handler action then releases lock on close"() {
        def lock = Mock(FileLock)

        given:
        lockManager.lock(file, lockOptions, "<cache>") >> lock
        cacheAccess.open()

        when:
        cacheAccess.close()

        then:
        1 * onCloseAction.execute(lock)

        then:
        1 * lock.close()
        0 * _
    }

    def "does not run handler on close when not open"() {
        when:
        cacheAccess.close()

        then:
        0 * _
    }
}
