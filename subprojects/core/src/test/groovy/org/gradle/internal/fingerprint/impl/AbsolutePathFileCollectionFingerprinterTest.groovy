/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.fingerprint.impl

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.execution.history.changes.AbsolutePathFingerprintCompareStrategy
import org.gradle.internal.execution.history.changes.ChangeTypeInternal
import org.gradle.internal.execution.history.changes.DefaultFileChange
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AbsolutePathFileCollectionFingerprinterTest extends Specification {
    def virtualFileSystem = TestFiles.virtualFileSystem()
    def fileSystemAccess = TestFiles.fileSystemAccess(virtualFileSystem)
    def fileCollectionSnapshotter = new DefaultFileCollectionSnapshotter(fileSystemAccess, TestFiles.fileSystem())
    def fingerprinter = new AbsolutePathFileCollectionFingerprinter(DirectorySensitivity.DEFAULT, fileCollectionSnapshotter, FileSystemLocationSnapshotHasher.DEFAULT)
    def listener = Mock(ChangeListener)

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "retains order of files in the snapshot"() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')
        TestFile file3 = tmpDir.createFile('file3')

        when:
        def fingerprint = fingerprinter.fingerprint(files(file, file2, file3))

        then:
        fingerprint.fingerprints.keySet().collect { new File(it) } == [file, file2, file3]
    }

    def "fingerprint includes existing root elements"() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile dir = tmpDir.createDir('dir')
        TestFile dir2 = dir.createDir('dir2')
        TestFile file2 = dir2.createFile('file2')
        TestFile noExist = tmpDir.file('file3')

        when:
        def fingerprint = fingerprinter.fingerprint(files(file, dir, noExist))

        then:
        fingerprint.fingerprints.keySet().collect { new File(it) } == [file, dir, dir2, file2]
    }

    def "retains order of elements in the snapshot"() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createDir('file2')
        TestFile file3 = tmpDir.createDir('file3')
        TestFile file4 = tmpDir.createFile('file4')

        when:
        def fingerprint = fingerprinter.fingerprint(files(file, file2, file3, file4))

        then:
        fingerprint.fingerprints.keySet().collect { new File(it) } == [file, file2, file3, file4]
    }

    def generatesEventWhenFileAdded() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        def fingerprint = fingerprinter.fingerprint(files(file1))
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(file1, file2)), listener)

        then:
        1 * listener.added(file2.path)
        0 * _
    }

    def generatesEventWhenFileRemoved() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(files(file1, file2))
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(file1)), listener)

        then:
        1 * listener.removed(file2.path)
        0 * _
    }

    def doesNotGenerateEventForFileWhoseTypeAndMetaDataAndContentHaveNotChanged() {
        given:
        TestFile file = tmpDir.createFile('file')
        file.setLastModified(1234L)

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(files(file))
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(file)), listener)
        file.setLastModified(45600L)
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(file)), listener)

        then:
        0 * listener._
    }

    def generatesEventWhenFileBecomesADirectory() {
        given:
        TestFile root = tmpDir.createDir('root')
        TestFile file = root.createFile('file')
        def fileCollection = files(root)

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(fileCollection)
        file.delete()
        file.createDir()
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(fileCollection), listener)

        then:
        1 * listener.changed(file.path)
        0 * _
    }

    def generatesEventWhenFileContentChanges() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(files(file))
        file.write('new content')
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(file)), listener)

        then:
        1 * listener.changed(file.path)
        0 * _
    }

    def doesNotGenerateEventForDirectoryThatHasNotChanged() {
        TestFile dir = tmpDir.createDir('dir')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(files(dir))
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(dir)), listener)

        then:
        0 * _
    }

    def generatesEventForDirectoryThatBecomesAFile() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile dir = root.createDir('dir')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(fileCollection)
        dir.deleteDir()
        dir.createFile()
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(fileCollection), listener)

        then:
        1 * listener.changed(dir.path)
        0 * listener._
    }

    def doesNotGenerateEventForMissingFileThatStillIsMissing() {
        TestFile file = tmpDir.file('unknown')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(files(file))
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(file)), listener)

        then:
        0 * _
    }

    def generatesEventWhenMissingFileIsCreated() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.file('newfile')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(fileCollection)
        file.createFile()
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(fileCollection), listener)

        then:
        1 * listener.added(file.path)
    }

    def generatesEventWhenFileIsDeleted() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.createFile('file')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(fileCollection)
        file.delete()
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(fileCollection), listener)

        then:
        1 * listener.removed(file.path)
    }

    def ignoresDuplicatesInFileCollection() {
        TestFile file1 = tmpDir.createFile('file')
        TestFile file2 = tmpDir.createFile('file')

        when:
        FileCollectionFingerprint fingerprint = fingerprinter.fingerprint(files(file1, file2))
        virtualFileSystem.invalidateAll()
        changes(fingerprint, fingerprinter.fingerprint(files(file1)), listener)

        then:
        0 * _
    }

    def canCreateEmptySnapshot() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionFingerprint fingerprint = FileCollectionFingerprint.EMPTY
        FileCollectionFingerprint newFingerprint = fingerprinter.fingerprint(files(file))
        virtualFileSystem.invalidateAll()
        changes(fingerprint, newFingerprint, listener)

        then:
        fingerprint.fingerprints.isEmpty()
        1 * listener.added(file.path)
        0 * listener._
    }

    private static void changes(FileCollectionFingerprint previous, FileCollectionFingerprint current, ChangeListener<String> listener) {
        AbsolutePathFingerprintCompareStrategy.INSTANCE.visitChangesSince(previous, current, "TYPE") { DefaultFileChange change ->
            switch (change.type) {
                case ChangeTypeInternal.ADDED:
                    listener.added(change.path)
                    break
                case ChangeTypeInternal.MODIFIED:
                    listener.changed(change.path)
                    break
                case ChangeTypeInternal.REMOVED:
                    listener.removed(change.path)
                    break
            }
            return true
        }
    }

    private static FileCollection files(File... files) {
        TestFiles.fixed(files)
    }
}

interface ChangeListener<T> {
    void added(T element);

    void removed(T element);

    void changed(T element);
}
