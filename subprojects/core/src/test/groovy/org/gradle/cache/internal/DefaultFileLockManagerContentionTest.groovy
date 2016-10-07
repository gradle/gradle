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

import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler
import org.gradle.cache.internal.locklistener.FileLockContentionHandler
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.internal.FileLockManager.LockMode.Shared

class DefaultFileLockManagerContentionTest extends ConcurrentSpec {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    FileLockContentionHandler contentionHandler = new DefaultFileLockContentionHandler(executorFactory, new InetAddressFactory())
    FileLockContentionHandler contentionHandler2 = new DefaultFileLockContentionHandler(executorFactory, new InetAddressFactory())
    FileLockManager manager = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler, new LongIdGenerator())
    FileLockManager manager2 = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler2, new LongIdGenerator())

    List<Closeable> openedLocks = []

    def cleanup() {
        CompositeStoppable.stoppable(openedLocks).add(contentionHandler, contentionHandler2).stop()
    }

    @Unroll
    def "lock manager is notified while holding an exclusive lock when another lock manager in same process requires lock with mode #lockMode"() {
        given:
        def file = tmpDir.file("lock-file.bin")
        def action = Mock(Runnable)

        def lock = createLock(Exclusive, file)
        manager.allowContention(lock, action)

        when:
        def lock2 = createLock(lockMode, file, manager2)

        then:
        lock2
        1 * action.run() >> {
            lock.close()
        }

        where:
        lockMode << [Exclusive, Shared]
    }

    @Unroll
    def "cannot acquire lock with mode #lockMode while another lock manager in same process is holding shared lock"() {
        given:
        def file = tmpDir.file("lock-file.bin")
        createLock(Shared, file)

        when:
        createLock(lockMode, file, manager2)

        then:
        thrown(LockTimeoutException)

        where:
        lockMode << [Exclusive, Shared]
    }

    FileLock createLock(LockMode lockMode, File file, FileLockManager lockManager = manager) {
        def lock = lockManager.lock(file, LockOptionsBuilder.mode(lockMode), "foo", "operation")
        openedLocks << lock
        lock
    }
}
