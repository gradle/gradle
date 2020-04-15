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

class DefaultFileLockManagerWithCrossVersionProtocolTest extends AbstractFileLockManagerTest {
    @Override
    protected LockOptionsBuilder options() {
        return LockOptionsBuilder.mode(FileLockManager.LockMode.OnDemand).useCrossVersionImplementation()
    }

    void isVersionLockFile(TestFile lockFile, boolean dirty) {
        assert lockFile.isFile()
        assert lockFile.length() == 2
        lockFile.withDataInputStream { str ->
            assert str.readByte() == 1
            assert str.readBoolean() != dirty
        }
    }

    @Override
    void isVersionLockFileWithInfoRegion(TestFile lockFile, boolean dirty, String processIdentifier, String operationalName) {
        assert lockFile.isFile()
        lockFile.withDataInputStream { str ->
            // state version + dirty flag
            assert str.readByte() == 1
            assert str.readBoolean() != dirty
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
