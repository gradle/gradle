/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FileLockManager.LockMode.Shared

class DefaultFileLockManagerWithNewProtocolTest extends AbstractFileLockManagerTest {
    @Override
    protected LockOptionsBuilder options() {
        return new LockOptionsBuilder(FileLockManager.LockMode.OnDemand)
    }

    def "a lock has been updated when never written to"() {
        given:
        def lock = createLock(Shared)
        def state = lock.state
        lock.close()

        when:
        lock = createLock(lockMode)

        then:
        lock.state.hasBeenUpdatedSince(state)

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has not been updated when locking an existing file that has not been accessed since last open"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        and:
        def lock = createLock(lockMode)
        def state = lock.state
        lock.close()

        when:
        lock = createLock(lockMode)

        then:
        !lock.state.hasBeenUpdatedSince(state)

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has not been updated when locking an existing file that has been read by another process since last open"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        and:
        def lock = createLock(lockMode)
        def beforeAccess = lock.state
        lock.close()

        and:
        lock = createLock(lockMode, testFile, lockManager)
        lock.readFile {}
        lock.close()

        when:
        lock = createLock(lockMode)

        then:
        !lock.state.hasBeenUpdatedSince(beforeAccess)

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when written to by another process since last open"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        and:
        def lock = createLock(lockMode)
        def beforeUpdate = lock.state
        lock.close()

        and:
        writeFile(lockManager)
        writeFile(lockManager)

        when:
        lock = createLock(lockMode)
        def afterUpdate = lock.state

        then:
        lock.state.hasBeenUpdatedSince(beforeUpdate)

        when:
        lock.close()
        lock = createLock(lockMode)

        then:
        lock.state.hasBeenUpdatedSince(beforeUpdate)
        !lock.state.hasBeenUpdatedSince(afterUpdate)

        when:
        lock.close()
        writeFile(lockManager)
        lock = createLock(lockMode)

        then:
        lock.state.hasBeenUpdatedSince(beforeUpdate)
        lock.state.hasBeenUpdatedSince(afterUpdate)

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when written to while open"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        and:
        def lock = createLock(Exclusive)
        def beforeUpdate = lock.state

        when:
        lock.writeFile { }

        then:
        lock.state.hasBeenUpdatedSince(beforeUpdate)

        cleanup:
        lock?.close()
    }

    def "a lock has been updated when lock is dirty"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        and:
        def lock = createLock(lockMode)
        def state = lock.state
        lock.close()

        and:
        unlockUncleanly(lockManager)

        when:
        lock = createLock(lockMode)

        then:
        !lock.unlockedCleanly
        lock.state.hasBeenUpdatedSince(state)

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when lock is partially written"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        and:
        def lock = createLock(lockMode)
        def state = lock.state
        lock.close()

        and:
        partiallyWritten(lockManager)

        when:
        lock = createLock(lockMode)

        then:
        !lock.unlockedCleanly
        lock.state.hasBeenUpdatedSince(state)

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when lock file has been recreated by another process"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        and:
        def lock = createLock(lockMode)
        def state = lock.state
        lock.close()

        and:
        testFileLock.delete()
        writeFile(lockManager)

        when:
        lock = createLock(lockMode)

        then:
        lock.unlockedCleanly
        lock.state.hasBeenUpdatedSince(state)

        where:
        lockMode << [Exclusive, Shared]
    }

    void isVersionLockFile(TestFile lockFile, boolean dirty) {
        assert lockFile.isFile()
        assert lockFile.length() <= 2048
        lockFile.withDataInputStream { str ->
            // state version + creation number + sequence number
            assert str.readByte() == 3
            str.readLong()
            if (dirty) {
                assert str.readLong() == 0
            } else {
                assert str.readLong() != 0
            }
            assert str.read() < 0
        }
    }

    void isVersionLockFileWithInfoRegion(TestFile lockFile, boolean dirty, String processIdentifier, String operationalName) {
        assert lockFile.isFile()
        assert lockFile.length() <= 2048
        lockFile.withDataInputStream { str ->
            // state version + creation number + sequence number
            assert str.readByte() == 3
            str.readLong()
            if (dirty) {
                assert str.readLong() == 0
            } else {
                assert str.readLong() != 0
            }
            // info version + port, lock-id, pid, operation-name
            assert str.readByte() == 3
            assert str.readInt() == 34
            assert str.readLong() == 678L
            assert str.readUTF() == processIdentifier
            assert str.readUTF() == operationalName
            assert str.read() < 0
        }
    }
}
