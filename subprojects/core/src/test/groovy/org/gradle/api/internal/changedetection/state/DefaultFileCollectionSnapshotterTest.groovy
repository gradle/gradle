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
    def treeVisitor = new CachingTreeVisitor()
    def stringInterner = new StringInterner()
    def treeSnapshotCache = new TreeSnapshotRepository(cacheAccess, stringInterner)
    def snapshotter = new DefaultFileCollectionSnapshotter(fileSnapshotter, cacheAccess, stringInterner, TestFiles.resolver(), treeVisitor, treeSnapshotCache)
    def listener = Mock(ChangeListener)
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def setup() {
        fileSnapshotter.snapshot(_) >> { FileTreeElement fileTreeElement ->
            return Stub(FileSnapshot) {
                getHash() >> HashUtil.sha1(fileTreeElement.file)
            }
        }
        fileSnapshotter.snapshot(_) >> { File file ->
            return Stub(FileSnapshot) {
                getHash() >> HashUtil.sha1(file)
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
        def snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file, dir, noExist), true))

        then:
        snapshot.files as List == [file]
    }

    def generatesEventWhenFileAdded() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        def snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file1), true))
        changes(snapshotter.snapshot(snapshotter.preCheck(files(file1, file2), true)), snapshot, listener)

        then:
        1 * listener.added(file2.path)
        0 * _
    }

    def doesNotGenerateEventWhenFileAddedAndAddEventsAreFiltered() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.file('file2')
        TestFile file3 = tmpDir.createFile('file3')
        TestFile file4 = tmpDir.createDir('file4')

        when:
        def snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file1, file2), true))
        file2.createFile()
        def target = snapshotter.snapshot(snapshotter.preCheck(files(file1, file2, file3, file4), true))
        changes(target.iterateContentChangesSince(snapshot, EnumSet.of(FileCollectionSnapshot.ChangeFilter.IgnoreAddedFiles)), listener)

        then:
        0 * _
    }

    def generatesEventWhenFileRemoved() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file1, file2), true))
        changes(snapshotter.snapshot(snapshotter.preCheck(files(file1), true)), snapshot, listener)

        then:
        1 * listener.removed(file2.path)
        0 * _
    }

    def doesNotGenerateEventForFileWhoseTypeAndMetaDataAndContentHaveNotChanged() {
        given:
        TestFile file = tmpDir.createFile('file')
        file.setLastModified(1234L)

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file), true))
        changes(snapshotter.snapshot(snapshotter.preCheck(files(file), true)), snapshot, listener)
        file.setLastModified(45600L)
        changes(snapshotter.snapshot(snapshotter.preCheck(files(file), true)), snapshot, listener)

        then:
        0 * listener._
    }

    def generatesEventWhenFileBecomesADirectory() {
        given:
        TestFile root = tmpDir.createDir('root')
        TestFile file = root.createFile('file')
        def fileCollection = files(root)

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(fileCollection, true))
        treeVisitor.clearCache()
        file.delete()
        file.createDir()
        changes(snapshotter.snapshot(snapshotter.preCheck(fileCollection, true)), snapshot, listener)

        then:
        1 * listener.changed(file.path)
        0 * _
    }

    def generatesEventWhenFileContentChanges() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file), true))
        file.write('new content')
        changes(snapshotter.snapshot(snapshotter.preCheck(files(file), true)), snapshot, listener)

        then:
        1 * listener.changed(file.path)
        0 * _
    }

    def doesNotGenerateEventForDirectoryThatHasNotChanged() {
        TestFile dir = tmpDir.createDir('dir')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(files(dir), true))

        changes(snapshotter.snapshot(snapshotter.preCheck(files(dir), true)), snapshot, listener)

        then:
        0 * _
    }

    def generatesEventForDirectoryThatBecomesAFile() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile dir = root.createDir('dir')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(fileCollection, true))
        treeVisitor.clearCache()
        dir.deleteDir()
        dir.createFile()
        changes(snapshotter.snapshot(snapshotter.preCheck(fileCollection, true)), snapshot, listener)

        then:
        1 * listener.changed(dir.path)
        0 * listener._
    }

    def doesNotGenerateEventForMissingFileThatStillIsMissing() {
        TestFile file = tmpDir.file('unknown')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file), true))
        changes(snapshotter.snapshot(snapshotter.preCheck(files(file), true)), snapshot, listener)

        then:
        0 * _
    }

    def generatesEventWhenMissingFileIsCreated() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.file('newfile')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(fileCollection, true))
        treeVisitor.clearCache()
        file.createFile()
        changes(snapshotter.snapshot(snapshotter.preCheck(fileCollection, true)), snapshot, listener)

        then:
        1 * listener.added(file.path)
    }

    def generatesEventWhenFileIsDeleted() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(fileCollection, true))
        treeVisitor.clearCache()
        file.delete()
        changes(snapshotter.snapshot(snapshotter.preCheck(fileCollection, true)), snapshot, listener)

        then:
        1 * listener.removed(file.path)
    }

    def ignoresDuplicatesInFileCollection() {
        TestFile file1 = tmpDir.createFile('file')
        TestFile file2 = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(snapshotter.preCheck(files(file1, file2), true))
        changes(snapshotter.snapshot(snapshotter.preCheck(files(file1), true)), snapshot, listener)

        then:
        0 * _
    }

    def canCreateEmptySnapshot() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.emptySnapshot()
        FileCollectionSnapshot newSnapshot = snapshotter.snapshot(snapshotter.preCheck(files(file), true))
        changes(newSnapshot, snapshot, listener)

        then:
        snapshot.files.empty
        1 * listener.added(file.path)
        0 * listener._
    }

    private void changes(FileCollectionSnapshot newSnapshot, FileCollectionSnapshot oldSnapshot, ChangeListener<String> listener) {
        changes(newSnapshot.iterateContentChangesSince(oldSnapshot, [] as Set), listener)
    }

    private void changes(FileCollectionSnapshot.ChangeIterator<String> changes, ChangeListener<String> listener) {
        while (changes.next(listener)) {
        }
    }

    private FileCollection files(File... files) {
        new SimpleFileCollection(files)
    }

}
