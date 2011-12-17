/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.Factory
import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import org.junit.Rule
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 8/30/11
 */
class DefaultFileLockManagerTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()
    ProcessMetaDataProvider metaDataProvider = Mock()
    FileLockManager manager = new DefaultFileLockManager(metaDataProvider)

    def setup() {
        metaDataProvider.processIdentifier >> '123'
        metaDataProvider.processDisplayName >> 'process'
    }

    def "can lock a file"() {
        when:
        def lock = manager.lock(tmpDir.createFile("file.txt"), LockMode.Shared, "lock")

        then:
        lock.isLockFile(tmpDir.createFile("file.txt.lock"))

        cleanup:
        lock?.close()
    }

    def "can lock a directory"() {
        when:
        def lock = manager.lock(tmpDir.createDir("some-dir"), LockMode.Shared, "lock")

        then:
        lock.isLockFile(tmpDir.createFile("some-dir/some-dir.lock"))

        cleanup:
        lock?.close()
    }

    def "can lock a file once it has been closed"() {
        given:
        def fileLock = lock(FileLockManager.LockMode.Exclusive);
        fileLock.close()

        when:
        lock(FileLockManager.LockMode.Exclusive);

        then:
        notThrown(RuntimeException)
    }

    def "lock on new file is not unlocked cleanly"() {
        when:
        def lock = manager.lock(tmpDir.createFile("file.txt"), mode, "lock")

        then:
        !lock.unlockedCleanly

        cleanup:
        lock?.close()

        where:
        mode << [LockMode.Shared, LockMode.Exclusive]
    }

    def "existing lock is unlocked cleanly after writeToFile() has been called"() {
        when:
        def lock = manager.lock(tmpDir.createFile("file.txt"), LockMode.Exclusive, "lock")
        lock.writeToFile({} as Runnable)

        then:
        lock.unlockedCleanly

        when:
        lock.close()
        lock = manager.lock(tmpDir.createFile("file.txt"), LockMode.Exclusive, "lock")

        then:
        lock.unlockedCleanly

        cleanup:
        lock?.close()
    }

    def "existing lock is unlocked cleanly after writeToFile() throws exception"() {
        def failure = new RuntimeException()

        when:
        def lock = manager.lock(tmpDir.createFile("file.txt"), LockMode.Exclusive, "lock")
        lock.writeToFile({throw failure} as Runnable)

        then:
        RuntimeException e = thrown()
        e == failure
        !lock.unlockedCleanly

        when:
        lock.close()
        lock = manager.lock(tmpDir.createFile("file.txt"), LockMode.Exclusive, "lock")

        then:
        !lock.unlockedCleanly

        cleanup:
        lock?.close()
    }

    def "cannot lock a file twice in single process"() {
        given:
        lock(LockMode.Exclusive);

        when:
        lock(LockMode.Exclusive);

        then:
        thrown(IllegalStateException)
    }

    def "cannot lock twice in single process for mixed modes"() {
        given:
        lock(LockMode.Exclusive);

        when:
        lock(LockMode.Shared);

        then:
        thrown(IllegalStateException)
    }

    def "cannot lock twice in single process for shared mode"() {
        given:
        lock(LockMode.Shared);

        when:
        lock(LockMode.Shared);

        then:
        thrown(IllegalStateException)
    }

    def "can close a lock multiple times"() {
        given:
        def lock = lock(LockMode.Exclusive)
        lock.close()

        expect:
        lock.close()
    }

    def "cannot read from file after lock has been closed"() {
        given:
        def lock = lock(LockMode.Exclusive)
        lock.close()

        when:
        lock.readFromFile({} as Factory)

        then:
        thrown(IllegalStateException)
    }

    def "cannot write to file after lock has been closed"() {
        given:
        def lock = lock(LockMode.Exclusive)
        lock.close()

        when:
        lock.writeToFile({} as Runnable)

        then:
        thrown(IllegalStateException)
    }

    def "leaves version 1 lock file after exclusive lock on new file closed"() {
        def file = tmpDir.file("state.bin")
        def lockFile = tmpDir.file("state.bin.lock")

        when:
        def lock = manager.lock(file, LockMode.Exclusive, "foo")
        lock.close()

        then:
        lock.isLockFile(lockFile)

        and:
        isVersion1LockFile(lockFile)
    }

    def "leaves empty lock file after shared lock on new file closed"() {
        def file = tmpDir.file("state.bin")
        def lockFile = tmpDir.file("state.bin.lock")

        when:
        def lock = manager.lock(file, LockMode.Shared, "foo")
        lock.close()

        then:
        lock.isLockFile(lockFile)

        and:
        isEmptyLockFile(lockFile)
    }

    def "leaves version 1 lock file after lock on existing file is closed"() {
        def file = tmpDir.file("state.bin")
        def lockFile = tmpDir.file("state.bin.lock")
        lockFile.withDataOutputStream {
            it.writeByte(1)
            it.writeBoolean(false)
        }

        when:
        def lock = manager.lock(file, mode, "foo")
        lock.close()

        then:
        lock.isLockFile(lockFile)

        and:
        isVersion1LockFile(lockFile)

        where:
        mode << [LockMode.Shared, LockMode.Exclusive]
    }

    @Requires(TestPrecondition.NO_FILE_LOCK_ON_OPEN)
    def "writes version 2 lock file while exclusive lock is open"() {
        def file = tmpDir.file("state.bin")
        def lockFile = tmpDir.file("state.bin.lock")

        when:
        def lock = manager.lock(file, LockMode.Exclusive, "foo", "operation")

        then:
        lock.isLockFile(lockFile)

        and:
        isVersion2LockFile(lockFile)

        cleanup:
        lock?.close()
    }

    def "can acquire lock on partially written lock file"() {
        def file = tmpDir.file("state.bin")
        def lockFile = tmpDir.file("state.bin.lock")

        when:
        lockFile.withDataOutputStream {
            it.writeByte(1)
        }
        def lock = manager.lock(file, mode, "foo")

        then:
        lock.isLockFile(lockFile)
        lock.close()

        when:
        lockFile.withDataOutputStream {
            it.writeByte(1)
            it.writeBoolean(true)
            it.writeByte(2)
            it.writeByte(12)
        }
        lock = manager.lock(file, mode, "foo")

        then:
        lock.isLockFile(lockFile)
        lock.close()

        where:
        mode << [LockMode.Shared, LockMode.Exclusive]
    }

    def "fails to acquire lock on lock file with unknown version"() {
        def file = tmpDir.file("state.bin")
        def lockFile = tmpDir.file("state.bin.lock")
        lockFile.withDataOutputStream {
            it.writeByte(125)
        }

        when:
        manager.lock(file, mode, "foo")

        then:
        thrown(IllegalStateException)

        where:
        mode << [LockMode.Shared, LockMode.Exclusive]
    }

    private void isEmptyLockFile(TestFile lockFile) {
        assert lockFile.isFile()
        assert lockFile.length() == 0
    }

    private void isVersion1LockFile(TestFile lockFile) {
        assert lockFile.isFile()
        assert lockFile.length() == 2
        lockFile.withDataInputStream { str ->
            assert str.readByte() == 1
            assert !str.readBoolean()
        }
    }

    private void isVersion2LockFile(TestFile lockFile) {
        assert lockFile.isFile()
        assert lockFile.length() > 3
        lockFile.withDataInputStream { str ->
            assert str.readByte() == 1
            assert !str.readBoolean()
            assert str.readByte() == 2
            assert str.readUTF() == '123'
            assert str.readUTF() == 'operation'
            assert str.read() < 0
        }
    }

    private FileLock lock(LockMode lockMode) {
        return manager.lock(tmpDir.file("state.bin"), lockMode, "foo")
    }
}
