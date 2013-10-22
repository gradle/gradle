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

class DefaultFileLockManagerWithNewProtocolTest extends AbstractFileLockManagerTest {
    @Override
    protected LockOptionsBuilder options() {
        return LockOptionsBuilder.mode(FileLockManager.LockMode.None)
    }

    def "the lock knows if it has a new owner process"() {
        when:
        def lock = createLock(Exclusive)

        then:
        lock.hasNewOwner

        when:
        lock.writeFile({})

        then:
        lock.hasNewOwner

        when:
        lock.close()
        lock = createLock(Exclusive)

        then:
        !lock.hasNewOwner

        cleanup:
        lock?.close()
    }

    void isVersionLockFile(TestFile lockFile) {
        assert lockFile.isFile()
        assert lockFile.length() <= 2048
        lockFile.withDataInputStream { str ->
            // state version + owner id
            assert str.readByte() == 2
            assert str.readInt() == 0
            assert str.read() < 0
        }
    }

    void isVersionLockFileWithInfoRegion(TestFile lockFile, String processIdentifier, String operationalName) {
        assert lockFile.isFile()
        assert lockFile.length() <= 2048
        lockFile.withDataInputStream { str ->
            // state version + owner id
            assert str.readByte() == 2
            assert str.readInt() == 0
            // info version + port, lock-id, pid, operation-name
            assert str.readByte() == 3
            assert str.readInt() == -1
            assert str.readLong() == 678L
            assert str.readUTF() == processIdentifier
            assert str.readUTF() == operationalName
            assert str.read() < 0
        }
    }
}
