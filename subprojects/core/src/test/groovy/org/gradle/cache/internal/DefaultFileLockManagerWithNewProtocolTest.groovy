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

import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.internal.FileLockManager.LockMode.Shared

class DefaultFileLockManagerWithNewProtocolTest extends AbstractFileLockManagerTest {
    @Override
    protected LockOptionsBuilder options() {
        return LockOptionsBuilder.mode(FileLockManager.LockMode.None)
    }

    def "a lock has been updated when locking a new file for the first time"() {
        when:
        def lock = createLock(lockMode)

        then:
        lock.hasBeenUpdated

        when:
        lock.close()
        lock = createLock(lockMode)

        then:
        !lock.hasBeenUpdated

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when locking an existing file for the first time"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile(lockManager)

        when:
        def lock = createLock(lockMode)

        then:
        lock.hasBeenUpdated

        when:
        lock.close()
        lock = createLock(lockMode)

        then:
        !lock.hasBeenUpdated

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when written to since last open"() {
        given:
        def lockManager = new DefaultFileLockManager(metaDataProvider, contentionHandler)
        writeFile()

        when:
        def lock = createLock(lockMode)

        then:
        !lock.hasBeenUpdated

        when:
        lock.close()
        writeFile(lockManager)
        lock = createLock(lockMode)

        then:
        lock.hasBeenUpdated

        when:
        lock.close()
        lock = createLock(lockMode)

        then:
        !lock.hasBeenUpdated

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when lock is dirty"() {
        when:
        def lock = createLock(lockMode)

        then:
        lock.hasBeenUpdated

        when:
        lock.close()
        lock = createLock(lockMode)

        then:
        !lock.hasBeenUpdated

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    def "a lock has been updated when lock is partially written"() {
        when:
        def lock = createLock(lockMode)

        then:
        lock.hasBeenUpdated

        when:
        lock.close()
        lock = createLock(lockMode)

        then:
        !lock.hasBeenUpdated

        cleanup:
        lock?.close()

        where:
        lockMode << [Exclusive, Shared]
    }

    void isVersionLockFile(TestFile lockFile) {
        assert lockFile.isFile()
        assert lockFile.length() <= 2048
        lockFile.withDataInputStream { str ->
            // state version + owner id
            assert str.readByte() == 3
            assert str.readInt() == 0
            assert str.read() < 0
        }
    }

    void isVersionLockFileWithInfoRegion(TestFile lockFile, String processIdentifier, String operationalName) {
        assert lockFile.isFile()
        assert lockFile.length() <= 2048
        lockFile.withDataInputStream { str ->
            // state version + owner id
            assert str.readByte() == 3
            assert str.readInt() == 0
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
