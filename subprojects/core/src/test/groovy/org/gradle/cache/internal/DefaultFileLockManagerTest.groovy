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

import org.apache.commons.lang.RandomStringUtils
import org.gradle.cache.internal.FileLockManager.LockMode
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.internal.FileLockManager.LockMode.Shared

/**
 * @author: Szczepan Faber, created at: 8/30/11
 */
class DefaultFileLockManagerTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def metaDataProvider = Mock(ProcessMetaDataProvider)
    FileLockManager manager = new DefaultFileLockManager(metaDataProvider)

    TestFile testFile
    TestFile testFileLock
    TestFile testDir
    TestFile testDirLock

    def setup() {
        testFile = tmpDir.createFile("state.bin")
        testFileLock = tmpDir.file(testFile.name + ".lock")
        testDir = tmpDir.createDir("lockable-dir")
        testDirLock = tmpDir.file("${testDir.name}/${testDir.name}.lock")

        metaDataProvider.processIdentifier >> '123'
        metaDataProvider.processDisplayName >> 'process'
    }

    @Unroll
    "#operation throws integrity exception when not cleanly unlocked file"() {
        given:
        unlockUncleanly()

        and:
        def lock = createLock()

        when:
        lock."$operation"(arg)

        then:
        thrown FileIntegrityViolationException

        where:
        operation    | arg
        "readFile"   | {} as Factory
        "updateFile" | {} as Runnable
    }

    def "writeFile does not throw integrity exception when not cleanly unlocked file"() {
        given:
        unlockUncleanly()

        when:
        createLock(Exclusive).writeFile { }

        then:
        notThrown FileIntegrityViolationException
    }

    def "can lock a file"() {
        when:
        def lock = createLock()

        then:
        lock.isLockFile(tmpDir.createFile(testFile.name + ".lock"))

        cleanup:
        lock?.close()
    }

    def "can lock a directory"() {
        when:
        def lock = createLock(testDir)

        then:
        lock.isLockFile(testDirLock)

        cleanup:
        lock?.close()
    }

    def "can lock a file once it has been closed"() {
        given:
        def fileLock = createLock(Exclusive)
        fileLock.close()

        when:
        createLock(Exclusive)

        then:
        notThrown(RuntimeException)
    }

    def "lock on new file is not unlocked cleanly"() {
        when:
        def lock = createLock(mode)

        then:
        !lock.unlockedCleanly

        cleanup:
        lock?.close()

        where:
        mode << [Shared, Exclusive]
    }

    def "file is 'invalid' unless a valid lock file exists"() {
        given:
        def lock = createLock(Exclusive)

        when:
        lock.readFile({})

        then:
        thrown FileIntegrityViolationException

        when:
        lock.updateFile({})

        then:
        thrown FileIntegrityViolationException

        when:
        lock.writeFile({})

        and:
        lock.readFile({})
        lock.updateFile({})

        then:
        notThrown FileIntegrityViolationException
    }

    def "integrity violation exception is thrown after a failed write"() {
        given:
        def e = new RuntimeException()
        def lock = createLock()

        when:
        lock.writeFile {}
        lock.updateFile { throw e }

        then:
        thrown RuntimeException

        when:
        lock.readFile({})

        then:
        thrown FileIntegrityViolationException
    }

    def "existing lock is unlocked cleanly after writeToFile() has been called"() {
        when:
        def lock = this.createLock(Exclusive)
        lock.writeFile({})
        lock.updateFile({} as Runnable)

        then:
        lock.unlockedCleanly

        when:
        lock.close()
        lock = createLock(Exclusive)

        then:
        lock.unlockedCleanly

        cleanup:
        lock?.close()
    }

    def "existing lock is unlocked cleanly after writeToFile() throws exception"() {
        def failure = new RuntimeException()

        when:
        def lock = createLock(Exclusive)
        lock.writeFile({ })
        lock.updateFile({throw failure} as Runnable)

        then:
        RuntimeException e = thrown()
        e == failure
        !lock.unlockedCleanly

        when:
        lock.close()
        lock = createLock(Exclusive)

        then:
        !lock.unlockedCleanly

        cleanup:
        lock?.close()
    }

    def "cannot lock a file twice in single process"() {
        given:
        createLock(Exclusive);

        when:
        createLock(Exclusive);

        then:
        thrown(IllegalStateException)
    }

    def "cannot lock twice in single process for mixed modes"() {
        given:
        createLock(Exclusive);

        when:
        createLock(Shared);

        then:
        thrown(IllegalStateException)
    }

    def "cannot lock twice in single process for shared mode"() {
        given:
        createLock(Shared);

        when:
        createLock(Shared);

        then:
        thrown(IllegalStateException)
    }

    def "can close a lock multiple times"() {
        given:
        def lock = createLock(Exclusive)
        lock.close()

        expect:
        lock.close()
    }

    def "cannot read from file after lock has been closed"() {
        given:
        def lock = createLock(Exclusive)
        lock.close()

        when:
        lock.readFile({} as Factory)

        then:
        thrown(IllegalStateException)
    }

    def "cannot write to file after lock has been closed"() {
        given:
        def lock = createLock(Exclusive)
        lock.close()

        when:
        lock.updateFile({} as Runnable)

        then:
        thrown(IllegalStateException)
    }

    def "leaves version 1 lock file after exclusive lock on new file closed"() {
        when:
        def lock = createLock(Exclusive)
        lock.close()

        then:
        lock.isLockFile(testFileLock)

        and:
        isVersion1LockFile(testFileLock)
    }

    def "leaves empty lock file after shared lock on new file closed"() {
        when:
        def lock = createLock()
        lock.close()

        then:
        lock.isLockFile(testFileLock)

        and:
        isEmptyLockFile(testFileLock)
    }

    def "leaves version 1 lock file after lock on existing file is closed"() {
        testFileLock.withDataOutputStream {
            it.writeByte(1)
            it.writeBoolean(false)
        }

        when:
        def lock = createLock(mode)
        lock.close()

        then:
        lock.isLockFile(testFileLock)

        and:
        isVersion1LockFile(testFileLock)

        where:
        mode << [Shared, Exclusive]
    }

    @Requires(TestPrecondition.NO_FILE_LOCK_ON_OPEN)
    def "writes version 2 lock file while exclusive lock is open"() {
        when:
        def lock = createLock(Exclusive)

        then:
        lock.isLockFile(testFileLock)

        and:
        isVersion2LockFile(testFileLock)

        cleanup:
        lock?.close()
    }

    def "can acquire lock on partially written lock file"() {
        when:
        testFileLock.withDataOutputStream {
            it.writeByte(1)
        }
        def lock = createLock(mode)

        then:
        lock.isLockFile(testFileLock)
        lock.close()

        when:
        testFileLock.withDataOutputStream {
            it.writeByte(1)
            it.writeBoolean(true)
            it.writeByte(2)
            it.writeByte(12)
        }
        lock = createLock(mode)

        then:
        lock.isLockFile(testFileLock)
        lock.close()

        where:
        mode << [Shared, Exclusive]
    }

    def "fails to acquire lock on lock file with unknown version"() {
        testFileLock.withDataOutputStream {
            it.writeByte(125)
        }

        when:
        createLock(mode)

        then:
        thrown(IllegalStateException)

        where:
        mode << [Shared, Exclusive]
    }

    @Requires(TestPrecondition.NO_FILE_LOCK_ON_OPEN)
    def "long descriptor strings are trimmed when written to information region"() {
        setup:
        def customMetaDataProvider = Mock(ProcessMetaDataProvider)
        def processIdentifier = RandomStringUtils.randomAlphanumeric(1000)
        1 * customMetaDataProvider.processIdentifier >> processIdentifier
        def customManager = new DefaultFileLockManager(customMetaDataProvider)
        def operationalDisplayName = RandomStringUtils.randomAlphanumeric(1000)

        when:
        customManager.lock(testFile, Exclusive, "targetDisplayName", operationalDisplayName)

        then:
        isVersion2LockFile(testFileLock, processIdentifier.substring(0, DefaultFileLockManager.INFORMATION_REGION_DESCR_CHUNK_LIMIT), operationalDisplayName.substring(0, DefaultFileLockManager.INFORMATION_REGION_DESCR_CHUNK_LIMIT))
    }

    def "require exclusive lock for writing"() {
        given:
        def lock = createLock(Shared)

        when:
        lock.writeFile {}

        then:
        thrown InsufficientLockModeException
    }

    def "require exclusive lock for updating"() {
        given:
        def writeLock = createLock(Exclusive)
        writeLock.writeFile {}
        writeLock.close()

        def lock = createLock(Shared)

        when:
        lock.updateFile {}

        then:
        thrown InsufficientLockModeException
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

    private void isVersion2LockFile(TestFile lockFile, String processIdentifier = "123", String operationalName = 'operation') {
        assert lockFile.isFile()
        assert lockFile.length() > 3
        assert lockFile.length() <= 2048
        lockFile.withDataInputStream { str ->
            assert str.readByte() == 1
            assert !str.readBoolean()
            assert str.readByte() == 2
            assert str.readUTF() == processIdentifier
            assert str.readUTF() == operationalName
            assert str.read() < 0
        }
    }

    private FileLock createLock(File testFile) {
        createLock(Shared, testFile)
    }

    private FileLock createLock(LockMode lockMode = Shared, File file = testFile) {
        manager.lock(file, lockMode, "foo", "operation")
    }

    private File unlockUncleanly(LockMode lockMode = Shared, File file = testFile) {
        DefaultFileLockManagerTestHelper.unlockUncleanly(file)
    }
}
