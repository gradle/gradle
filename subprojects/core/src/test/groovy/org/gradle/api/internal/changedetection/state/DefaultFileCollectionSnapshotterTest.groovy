/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.changedetection.state

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ChangeListener
import org.junit.Rule
import spock.lang.Specification

public class DefaultFileCollectionSnapshotterTest extends Specification {
    def fileSnapshotter = Stub(FileSnapshotter)
    def cacheAccess = Stub(TaskArtifactStateCacheAccess)
    def snapshotter = new DefaultFileCollectionSnapshotter(fileSnapshotter, cacheAccess, new StringInterner(), TestFiles.resolver())

    def listener = Mock(ChangeListener)
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def setup() {
        fileSnapshotter.snapshot(_) >> { FileTreeElement fileTreeElement ->
            return Stub(FileSnapshot) {
                getHash() >> HashUtil.sha1(fileTreeElement.file).asByteArray()
            }
        }
        fileSnapshotter.snapshot(_) >> { File file ->
            return Stub(FileSnapshot) {
                getHash() >> HashUtil.sha1(file).asByteArray()
            }
        }
        cacheAccess.useCache(_, _) >> { String name, Runnable action ->
            action.run()
        }
    }

    def getFilesReturnsOnlyTheFilesWhichExisted() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile dir = tmpDir.createDir('file2')
        TestFile noExist = tmpDir.file('file3')

        when:
        def snapshot = snapshotter.snapshot(files(file, dir, noExist))

        then:
        snapshot.files as List == [file]
    }

    def notifiesListenerWhenFileAdded() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        def snapshot = snapshotter.snapshot(files(file1))
        snapshotter.snapshot(files(file1, file2)).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.added(file2.path)
        0 * _
    }

    def notifiesListenerWhenFileRemoved() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1, file2))
        snapshotter.snapshot(files(file1)).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.removed(file2.path)
        0 * _
    }

    def fileHasNotChangedWhenTypeAndHashHaveNotChanged() {
        given:
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))
        snapshotter.snapshot(files(file)).iterateChangesSince(snapshot).next(listener)

        then:
        _ * listener.stopped >> false
        _ * listener.resumeAfter >> null
        0 * listener._
    }

    def fileHasChangedWhenTypeHasChanged() {
        given:
        TestFile root = tmpDir.createDir('root')
        TestFile file = root.createFile('file')
        def fileCollection = files(root)

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection)
        file.delete()
        file.createDir()
        snapshotter.snapshot(fileCollection).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.changed(file.path)

        and:
        _ * listener.stopped >> false
        _ * listener.resumeAfter >> null
        0 * _
    }

    def fileHasChangedWhenHashHasChanged() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))
        file.write('new content')
        snapshotter.snapshot(files(file)).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.changed(file.path)

        and:
        _ * listener.stopped >> false
        _ * listener.resumeAfter >> null
        0 * _
    }

    def directoryHasNotChangedWhenTypeHasNotChanged() {
        TestFile dir = tmpDir.createDir('dir')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(dir))

        snapshotter.snapshot(files(dir)).iterateChangesSince(snapshot).next(listener)

        then:
        _ * listener.stopped >> false
        _ * listener.resumeAfter >> null
        0 * _
    }

    def directoryHasChangedWhenTypeHasChanged() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile dir = root.createDir('dir')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection)
        dir.deleteDir()
        dir.createFile()
        snapshotter.snapshot(fileCollection).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.changed(dir.path)
    }

    def nonExistentFileUnchangedWhenTypeHasNotChanged() {
        TestFile file = tmpDir.file('unknown')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))
        snapshotter.snapshot(files(file)).iterateChangesSince(snapshot).next(listener)

        then:
        _ * listener.stopped >> false
        _ * listener.resumeAfter >> null
        0 * _
    }

    def newFileIsAdded() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.file('newfile')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection)
        file.createFile()
        snapshotter.snapshot(fileCollection).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.added(file.path)
    }

    def deletedFileIsRemoved() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection)
        file.delete()
        snapshotter.snapshot(fileCollection).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.removed(file.path)
    }

    def ignoresDuplicatesInFileCollection() {
        TestFile file1 = tmpDir.createFile('file')
        TestFile file2 = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1, file2))
        snapshotter.snapshot(files(file1)).iterateChangesSince(snapshot).next(listener)

        then:
        0 * _
    }

    def canCreateEmptySnapshot() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.emptySnapshot()
        FileCollectionSnapshot newSnapshot = snapshotter.snapshot(files(file))
        changes(newSnapshot.iterateChangesSince(snapshot), listener)

        then:
        snapshot.files.empty
        1 * listener.added(file.path)
        0 * listener._
    }

    def diffAddsAddedFilesToSnapshot() {
        TestFile file = tmpDir.createFile('file')

        given:
        FileCollectionSnapshot original = snapshotter.emptySnapshot()
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        when:
        FileCollectionSnapshot target = modified.applyChangesSince(original, snapshotter.emptySnapshot())
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files == [file]
        1 * listener.added(file.path)
        0 * listener._
    }

    def diffAddsChangedFilesToSnapshot() {
        TestFile file = tmpDir.createFile('file')

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        file.write('new content')
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        when:
        FileCollectionSnapshot target = modified.applyChangesSince(original, snapshotter.emptySnapshot())
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files == [file]
        1 * listener.changed(file.path)
        0 * listener._
    }

    def diffRemovesDeletedFilesFromSnapshot() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.emptySnapshot()
        FileCollectionSnapshot target = modified.applyChangesSince(original, snapshotter.snapshot(files(file)))
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files.empty
        1 * listener.removed(file.path)
        0 * listener._
    }

    def diffIgnoresUnchangedFilesInSnapshot() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))
        FileCollectionSnapshot target = modified.applyChangesSince(original, snapshotter.emptySnapshot())

        changes(target.iterateChangesSince(snapshotter.emptySnapshot()), listener)

        then:
        target.files.empty
        0 * _
    }

    def updatesFileThatHasChangedContentInSourceSnapshot() {
        TestFile file = tmpDir.createFile('file')

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        file.write('new content')
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        when:
        FileCollectionSnapshot target = original.updateFrom(modified)
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files == [file]
        1 * listener.changed(file.path)
        0 * listener._
    }

    def updatesFileThatHasChangedTypeInSourceSnapshot() {
        TestFile file1 = tmpDir.createFile('1')
        TestFile file2 = tmpDir.createDir('2')

        FileCollectionSnapshot original = snapshotter.snapshot(files(file1, file2))
        file1.delete()
        file1.createDir()
        file2.deleteDir()
        file2.createFile()
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file1, file2))

        when:
        FileCollectionSnapshot target = original.updateFrom(modified)
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files.empty
        1 * listener.removed(file1.path)
        0 * listener._
    }

    def removesFileThatIsNotInSourceSnapshot() {
        TestFile file1 = tmpDir.createFile('1')
        TestFile file2 = tmpDir.createFile('2')

        FileCollectionSnapshot original = snapshotter.snapshot(files(file1, file2))
        FileCollectionSnapshot modified = snapshotter.snapshot(files())

        when:
        FileCollectionSnapshot target = original.updateFrom(modified)
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files.empty
        1 * listener.removed(file1.path)
        1 * listener.removed(file2.path)
        0 * listener._
    }

    def ignoresFileThatIsOnlyInSourceSnapshot() {
        TestFile file1 = tmpDir.createFile('1')
        TestFile file2 = tmpDir.createFile('2')
        TestFile file3 = tmpDir.createDir('3')
        TestFile file4 = tmpDir.file('4')

        FileCollectionSnapshot original = snapshotter.snapshot(files(file1))
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file1, file2, file3, file4))

        when:
        FileCollectionSnapshot target = original.updateFrom(modified)
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files == [file1]
        0 * listener._
    }

    def ignoresFileThatHasNotChangedInSourceSnapshot() {
        TestFile file1 = tmpDir.createFile('1')
        TestFile file2 = tmpDir.createDir('2')
        TestFile file3 = tmpDir.file('3')

        FileCollectionSnapshot original = snapshotter.snapshot(files(file1, file2, file3))
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file1, file2, file3))

        when:
        FileCollectionSnapshot target = original.updateFrom(modified)
        changes(target.iterateChangesSince(original), listener)

        then:
        target.files == [file1]
        0 * listener._
    }

    private void changes(FileCollectionSnapshot.ChangeIterator<String> changes, ChangeListener<String> listener) {
        while (changes.next(listener)) {
        }
    }

    private FileCollection files(File... files) {
        new SimpleFileCollection(files)
    }

}
