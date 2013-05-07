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
import org.gradle.api.file.FileTree
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ChangeListener
import org.junit.Rule
import spock.lang.Specification

public class DefaultFileSnapshotterTest extends Specification {
    private final Hasher hasher = new DefaultHasher()
    private final DefaultFileSnapshotter snapshotter = new DefaultFileSnapshotter(hasher)
    def listener = Mock(ChangeListener)
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def getFilesReturnsOnlyTheFilesWhichExisted() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile dir = tmpDir.createDir('file2')
        TestFile noExist = tmpDir.file('file3')

        when:
        def snapshot = snapshotter.snapshot(files(file, dir, noExist))

        then:
        snapshot.files.files as List == [file]
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
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))
        file.delete()
        file.createDir()
        snapshotter.snapshot(files(file)).iterateChangesSince(snapshot).next(listener)

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
        TestFile dir = tmpDir.createDir('dir')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(dir))
        dir.deleteDir()
        dir.createFile()
        snapshotter.snapshot(files(dir)).iterateChangesSince(snapshot).next(listener)

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

    def nonExistentFileIsChangedWhenTypeHasChanged() {
        TestFile file = tmpDir.file('unknown')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file))
        file.createFile()
        snapshotter.snapshot(files(file)).iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.changed(file.path)
    }

    def ignoresDuplicatesInFileCollection() {
        TestFile file1 = tmpDir.createFile('file')
        TestFile file2 = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1, file2))
        snapshotter.snapshot(files(file1)).iterateChangesSince(snapshot).next(listener)

        then:
        _ * listener.stopped >> false
        _ * listener.resumeAfter >> null
        0 * _
    }

    def canCreateEmptySnapshot() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.emptySnapshot()
        FileCollectionSnapshot newSnapshot = snapshotter.snapshot(files(file))
        newSnapshot.iterateChangesSince(snapshot).next(listener)

        then:
        1 * listener.added(file.path)
    }

    def diffAddsAddedFilesToSnapshot() {
        TestFile file = tmpDir.createFile('file')

        given:
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = Mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.emptySnapshot()
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        when:
        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)

        then:
        1 * mergeListener.added(_)

        when:
        target.iterateChangesSince(original).next(listener)

        then:
        1 * listener.added(file.path)
    }

    def canIgnoreAddedFileInDiff() {
        TestFile file = tmpDir.createFile('file')

        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = Mock(ChangeListener.class)
        FileCollectionSnapshot original = snapshotter.emptySnapshot()
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        when:
        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)
        target.iterateChangesSince(original).next(listener)

        then:
        mergeListener.added(!null) >> { FileCollectionSnapshot.Merge merge -> merge.ignore() }
    }

    def diffAddsChangedFilesToSnapshot() {
        TestFile file = tmpDir.createFile('file')

        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = Mock(ChangeListener.class)

        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        file.write('new content')
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        when:
        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)

        then:
        1 * mergeListener.changed(!null)

        when:
        target.iterateChangesSince(original).next(listener)

        then:
        1 * listener.changed(file.path)
    }

    def canIgnoreChangedFileInDiff() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = Mock(ChangeListener.class)

        when:
        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot target = snapshotter.snapshot(files(file))
        file.write('new content')
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))

        and:
        target = modified.changesSince(original).applyTo(target, mergeListener)
        target.iterateChangesSince(original).next(listener)

        then:
        1 * mergeListener.changed(!null) >> { FileCollectionSnapshot.Merge merge -> merge.ignore() }
    }

    def diffRemovesDeletedFilesFromSnapshot() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = Mock(ChangeListener.class)

        when:
        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.emptySnapshot()

        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.snapshot(files(file)), mergeListener)

        then:
        1 * mergeListener.removed(!null)

        when:
        target.iterateChangesSince(original).next(listener)

        then:
        1 * listener.removed(file.path)
    }

    def canIgnoreRemovedFileInDiff() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = Mock(ChangeListener.class)

        when:
        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.emptySnapshot()
        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.snapshot(files(file)), mergeListener)

        target.iterateChangesSince(original).next(listener)

        then:
        mergeListener.removed(!null) >> { FileCollectionSnapshot.Merge merge -> merge.ignore() }
    }

    def diffIgnoresUnchangedFilesInSnapshot() {
        TestFile file = tmpDir.createFile('file')
        ChangeListener<FileCollectionSnapshot.Merge> mergeListener = Mock(ChangeListener.class)

        when:
        FileCollectionSnapshot original = snapshotter.snapshot(files(file))
        FileCollectionSnapshot modified = snapshotter.snapshot(files(file))
        FileCollectionSnapshot target = modified.changesSince(original).applyTo(snapshotter.emptySnapshot(), mergeListener)

        target.iterateChangesSince(snapshotter.emptySnapshot()).next(listener)

        then:
        _ * listener.stopped >> false
        _ * listener.resumeAfter >> null
        0 * _
    }

    private FileCollection files(File... files) {
        FileTree collection = Mock(FileTree.class)
        _ * collection.asFileTree >> collection
        _ * collection.iterator() >> (files as List).iterator()
        return collection
    }
    
}
