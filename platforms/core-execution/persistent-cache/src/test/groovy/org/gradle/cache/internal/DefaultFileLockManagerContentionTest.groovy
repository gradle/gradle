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
import org.gradle.cache.LockTimeoutException
import org.gradle.cache.internal.filelock.DefaultLockOptions
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler
import org.gradle.cache.internal.locklistener.FileLockContentionHandler
import org.gradle.cache.internal.locklistener.InetAddressProvider
import org.gradle.cache.internal.locklistener.RejectingFileLockContentionHandler
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

import java.util.function.Consumer

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FileLockManager.LockMode.Shared

class DefaultFileLockManagerContentionTest extends ConcurrentSpec {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def addressFactory = new InetAddressFactory()
    def addressProvider = new InetAddressProvider() {
        @Override
        InetAddress getWildcardBindingAddress() {
            return addressFactory.wildcardBindingAddress
        }

        @Override
        InetAddress getCommunicationAddress() {
            return addressFactory.localBindingAddress
        }
    }
    FileLockContentionHandler contentionHandler = new DefaultFileLockContentionHandler(executorFactory, addressProvider)
    FileLockContentionHandler contentionHandler2 = new DefaultFileLockContentionHandler(executorFactory, addressProvider)
    FileLockManager manager = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler)
    FileLockManager manager2 = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler2)

    List<Closeable> openedLocks = []

    def cleanup() {
        CompositeStoppable.stoppable(openedLocks).add(contentionHandler, contentionHandler2).stop()
    }

    def "lock manager is notified while holding an exclusive lock when another lock manager in same process requires lock with mode #lockMode"() {
        given:
        def file = tmpDir.file("lock-file.bin")
        def action = Mock(Consumer)

        def lock = createLock(Exclusive, file, manager, action)

        when:
        def lock2 = createLock(lockMode, file, manager2)

        then:
        lock2
        1 * action.accept(_) >> { FileLockReleasedSignal signal ->
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
        FileLockManager manager3 = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, contentionHandler3)

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

    def "can create lock without contention handling when using rejecting handler"() {
        FileLockManager manager = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, new RejectingFileLockContentionHandler())
        def file = tmpDir.file("lock-file.bin")

        expect:
        createLock(Exclusive, file, manager, null)
    }

    def "can create lock with contention handling when using rejecting handler"() {
        FileLockManager manager = new DefaultFileLockManager(Stub(ProcessMetaDataProvider), 2000, new RejectingFileLockContentionHandler())
        def file = tmpDir.file("lock-file.bin")
        Consumer<FileLockReleasedSignal> whenContended = Mock()
        when:
        createLock(Exclusive, file, manager, whenContended)

        then:
        1 * whenContended.accept(_)
    }

    // On Windows we can't delete a file that is open
    @Requires(UnitTestPreconditions.NotWindows)
    def "#description protect against a deletion of a lock file #lockOptionsDescription"() {
        given:
        def file = tmpDir.file("lock-file.bin")
        def lockFile = tmpDir.file("lock-file.bin.lock")
        def action1 = Mock(Consumer)
        def action2 = Mock(Consumer)

        when:
        def lock1 = createLock(lockOptions, file, manager, action1)

        then:
        lock1
        lock1.isLockFile(lockFile)
        lockFile.exists()

        when:
        def lock2 = createLock(lockOptions, file, manager2, action2)

        then:
        lock2
        lock2.isLockFile(lockFile)
        lockFile.exists() == expectLockFileExistsAfterLock2
        1 * action1.accept(_) >> { FileLockReleasedSignal signal ->
            // Delete the lock file before releasing just after lock2 signals that it wants to access the lock
            assert lockFile.delete()
            assert !lockFile.exists()
            lock1.close()
            signal.trigger()
        }

        when:
        def lock3 = createLock(lockOptions, file, manager, null)

        then:
        lock3
        lock3.isLockFile(lockFile)
        expectedLock2ContentionCalls * action2.accept(_) >> { FileLockReleasedSignal signal ->
            lock2.close()
            signal.trigger()
        }

        where:
        description | expectedLock2ContentionCalls | lockOptionsDescription                  | lockOptions                                                                        | expectLockFileExistsAfterLock2
        "does NOT"  | 0                            | "WITHOUT ensure file system check flag" | DefaultLockOptions.mode(Exclusive)                                                 | false
        "does"      | 1                            | "WITH ensure file system check flag"    | DefaultLockOptions.mode(Exclusive).ensureAcquiredLockRepresentsStateOnFileSystem() | true
    }

    // On Windows we can't delete a file that is open
    @Requires(UnitTestPreconditions.NotWindows)
    def "#description protect against recreating a lock file #lockOptionsDescription"() {
        given:
        def file = tmpDir.file("lock-file.bin")
        def lockFile = tmpDir.file("lock-file.bin.lock")
        def lock1ContentionAction = Mock(Consumer)
        def lock2ContentionAction = Mock(Consumer)
        def recreationState = null

        when:
        def lock1 = createLock(lockOptions, file, manager, lock1ContentionAction)

        then:
        lock1
        lock1.isLockFile(lockFile)
        lockFile.exists()

        when:
        def lock2 = createLock(lockOptions, file, manager2, lock2ContentionAction)

        then:
        lock2
        lock2.isLockFile(lockFile)
        recreationState
        lock2.state
        lock2.state.hasBeenUpdatedSince(recreationState) == expectedUpdated
        1 * lock1ContentionAction.accept(_) >> { FileLockReleasedSignal signal ->
            // Simulate recreate of the lock file with a different state just after lock2 signals that it wants to access the lock
            assert lockFile.delete()
            assert !lockFile.exists()
            lock1.close()
            def recreatingLock = createLock(lockOptions, file, manager, lock1ContentionAction)
            recreationState = recreatingLock.state
            recreatingLock.close()
            assert recreatingLock.isLockFile(lockFile)
            assert lockFile.exists()
            signal.trigger()
        }

        when:
        def lock3 = createLock(lockOptions, file, manager, null)

        then:
        lock3
        lock3.isLockFile(lockFile)
        expectedLock2ContentionCalls * lock2ContentionAction.accept(_) >> { FileLockReleasedSignal signal ->
            lock2.close()
            signal.trigger()
        }

        where:
        description | expectedLock2ContentionCalls | expectedUpdated | lockOptionsDescription                  | lockOptions
        "does NOT"  | 0                            | true            | "WITHOUT ensure file system check flag" | DefaultLockOptions.mode(Exclusive)
        "does"      | 1                            | false           | "WITH ensure file system check flag"    | DefaultLockOptions.mode(Exclusive).ensureAcquiredLockRepresentsStateOnFileSystem()
    }

    @Requires(UnitTestPreconditions.Windows)
    def "lock file cannot be deleted while lock is held on Windows"() {
        given:
        def file = tmpDir.file("held.bin")
        def lockFile = tmpDir.file("held.bin.lock")

        when:
        FileLock lock = createLock(DefaultLockOptions.mode(Exclusive), file, manager, null)

        then:
        lock
        lock.isLockFile(lockFile)
        lockFile.exists()
        !lockFile.delete()
        lockFile.exists()
    }

    FileLock createLock(FileLockManager.LockMode lockMode, File file, FileLockManager lockManager = manager, Consumer<FileLockReleasedSignal> whenContended = null) {
        return createLock(DefaultLockOptions.mode(lockMode), file, lockManager, whenContended)
    }

    FileLock createLock(LockOptions lockOptions, File file, FileLockManager lockManager, Consumer<FileLockReleasedSignal> whenContended) {
        def lock = lockManager.lock(file, lockOptions, "foo", "operation", whenContended)
        openedLocks << lock
        lock
    }
}
