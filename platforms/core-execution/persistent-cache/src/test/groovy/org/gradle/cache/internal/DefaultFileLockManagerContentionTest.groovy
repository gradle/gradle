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
import org.gradle.cache.FileLockReleasedSignal
import org.gradle.cache.LockTimeoutException
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler
import org.gradle.cache.internal.locklistener.FileLockContentionHandler
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FileLockManager.LockMode.Shared

class DefaultFileLockManagerContentionTest extends ConcurrentSpec {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    FileLockContentionHandler contentionHandler = new DefaultFileLockContentionHandler(executorFactory, new InetAddressFactory())
    FileLockContentionHandler contentionHandler2 = new DefaultFileLockContentionHandler(executorFactory, new InetAddressFactory())
    FileLockManager manager = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler, new LongIdGenerator())
    FileLockManager manager2 = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler2, new LongIdGenerator())

    List<Closeable> openedLocks = []

    def cleanup() {
        CompositeStoppable.stoppable(openedLocks).add(contentionHandler, contentionHandler2).stop()
    }

    def "lock manager is notified while holding an exclusive lock when another lock manager in same process requires lock with mode #lockMode"() {
        given:
        def file = tmpDir.file("lock-file.bin")
        def action = Mock(Action)

        def lock = createLock(Exclusive, file, manager, action)

        when:
        def lock2 = createLock(lockMode, file, manager2)

        then:
        lock2
        1 * action.execute(_) >> { FileLockReleasedSignal signal ->
            lock.close()
            signal.trigger()
        }

        where:
        lockMode << [Exclusive, Shared]
    }

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

    def "lock manage resets the timeout if the lock owner changes"() {
        given:
        FileLockContentionHandler contentionHandler3 = Mock(FileLockContentionHandler)
        FileLockManager manager3 = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler3, new LongIdGenerator())

        int port1 = contentionHandler.communicator.socket.localPort
        int port2 = contentionHandler2.communicator.socket.localPort

        def file = tmpDir.file("lock-file.bin")
        FileLock lock1 = createLock(Exclusive, file, manager)
        FileLock lock2

        when:
        createLock(Exclusive, file, manager3)

        then:
        1 * contentionHandler3.maybePingOwner(port1, _, _, _, _) >> { int port, long lockId, String displayName, long timeElapsed, FileLockReleasedSignal signal ->
            assert timeElapsed < 20
            lock1.close()
            lock2 = createLock(Exclusive, file, manager2)
            Thread.sleep(50)
            return false
        }
        1 * contentionHandler3.maybePingOwner(port2, _, _, _, _)  >> { int port, long lockId, String displayName, long timeElapsed, FileLockReleasedSignal signal ->
            assert timeElapsed < 20
            lock2.close()
            return false
        }
    }

    FileLock createLock(FileLockManager.LockMode lockMode, File file, FileLockManager lockManager = manager, Action<FileLockReleasedSignal> whenContended = null) {
        def lock = lockManager.lock(file, LockOptionsBuilder.mode(lockMode), "foo", "operation", whenContended)
        openedLocks << lock
        lock
    }
}
