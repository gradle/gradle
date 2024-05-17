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
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Specification

import java.util.function.Consumer

class FixedExclusiveModeCrossProcessCacheAccessTest extends Specification {
    def file = new TestFile("some-file.lock")
    def lockManager = Mock(FileLockManager)
    def initAction = Mock(CacheInitializationAction)
    def onOpenAction = Mock(Consumer)
    def onCloseAction = Mock(Consumer)
    def cacheAccess = new FixedExclusiveModeCrossProcessCacheAccess("<cache>", file, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), lockManager, initAction, onOpenAction, onCloseAction)

    def "acquires lock then validates cache and runs handler action on open"() {
        def lock = Mock(FileLock)

        when:
        cacheAccess.open()

        then:
        1 * lockManager.lock(file, _, _, "", _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> false

        then:
        1 * onOpenAction.accept(lock)
        0 * _
    }

    def "acquires lock then initializes cache and runs handler action on open"() {
        def lock = Mock(FileLock)

        when:
        cacheAccess.open()

        then:
        1 * lockManager.lock(file, _, _, "", _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock)

        then:
        1 * onOpenAction.accept(lock)
        0 * _
    }

    def "releases lock when initialization fails"() {
        def lock = Mock(FileLock)
        def failure = new RuntimeException()

        when:
        cacheAccess.open()

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * lockManager.lock(file, _, _, "", _) >> lock

        then:
        1 * initAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initAction.initialize(lock) >> { throw failure }

        then:
        1 * lock.close()
        0 * _

        when:
        cacheAccess.close()

        then:
        0 * _
    }

    def "runs handler action then releases lock on close"() {
        def lock = Mock(FileLock)

        given:
        lockManager.lock(file, _, _, "", _) >> lock
        cacheAccess.open()

        when:
        cacheAccess.close()

        then:
        1 * onCloseAction.accept(lock)

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
