/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.ChangeListener
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@UsesNativeServices
class OutputFilesCollectionSnapshotterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    @Subject
    OutputFilesCollectionSnapshotter snapshotter
    def listener = Mock(ChangeListener)
    TestFile rootDir
    FileCollectionSnapshot previous
    FileCollectionSnapshot before
    FileCollectionSnapshot after
    FileCollectionSnapshot target

    def setup() {
        def cachingTreeVisitor = new CachingTreeVisitor()
        def stringInterner = new StringInterner()
        def mapping = Stub(CacheScopeMapping) {
            getBaseDirectory(_, _, _) >> {
                return testDir.createDir("history-cache")
            }
        }
        CacheRepository cacheRepository = new DefaultCacheRepository(mapping, new InMemoryCacheFactory())
        TaskArtifactStateCacheAccess cacheAccess = new DefaultTaskArtifactStateCacheAccess(TestUtil.createRootProject().gradle, cacheRepository, new NoOpDecorator())
        def hasher = new DefaultHasher()
        def fileSnapshotter = new CachingFileSnapshotter(hasher, cacheAccess, stringInterner)
        def treeSnapshotCache = new TreeSnapshotRepository(cacheAccess, stringInterner)
        def defaultSnapshotter = new DefaultFileCollectionSnapshotter(fileSnapshotter, cacheAccess, stringInterner, TestFiles.resolver(), cachingTreeVisitor, treeSnapshotCache)
        snapshotter = new OutputFilesCollectionSnapshotter(defaultSnapshotter, stringInterner)
        rootDir = testDir.createDir("root")
    }

    def "output snapshotting should ignore files created between executions"() {
        given:
        def file1 = rootDir.createFile("a/1.txt")
        def file2 = rootDir.createFile("b/2.txt")
        def file3 = rootDir.createFile("b/3.txt")
        def file4
        def file5
        snapshotBeforeAndAfterTasks({ file4 = rootDir.createFile("otheroutput/1.txt") }, { file5 = rootDir.createFile("taskcreated/1.txt") })

        when:
        createOutputSnapshot()

        then:
        target.files as Set == [file1, file2, file3, file5] as Set
    }

    def 'output snapshot detects new files'() {
        given:
        TestFile file
        snapshotBeforeAndAfterTasks(null) {
            file = rootDir.createFile('file')
        }

        when:
        createOutputSnapshot()

        then:
        target.files == [file]
        1 * listener.added(file.path)
        0 * listener._
    }

    def "output snapshot detects file metadata changes and ignores files that haven't changed"() {
        given:
        TestFile file = rootDir.createFile('file')
        snapshotBeforeAndAfterTasks({
            file = rootDir.createFile('file')
            file.setLastModified(2000L)
            TestFile file2 = rootDir.createFile('file')
        }, {
            file.setLastModified(4000L)
        })

        when:
        createOutputSnapshot()

        then:
        target.files == [file]
    }

    def "output snapshot detects deleted files"() {
        given:
        TestFile file = rootDir.createFile('file')
        snapshotBeforeAndAfterTasks(null, { file.delete() })

        when:
        createOutputSnapshot()

        then:
        target.files.empty
        1 * listener.removed(file.path)
        0 * listener._
    }

    def "output snapshot ignores unchanged files"() {
        given:
        snapshotBeforeAndAfterTasks({ rootDir.createFile('file') }, null)

        when:
        createOutputSnapshot()

        then:
        target.files.empty
        0 * _
    }

    def "output snapshot detects 2 new files"() {
        given:
        TestFile file1
        TestFile file2
        snapshotBeforeAndAfterTasks(null) {
            file1 = rootDir.createFile('file1')
            file2 = rootDir.createFile('file2')
        }

        when:
        createOutputSnapshot()

        then:
        target.files as Set == [file1, file2] as Set
        1 * listener.added(file1.path)
        1 * listener.added(file2.path)
        0 * _
    }

    def "output snapshot detects a file change"() {
        given:
        TestFile file = rootDir.createFile('file')
        snapshotBeforeAndAfterTasks(null) {
            file.write('new content')
        }

        when:
        createOutputSnapshot()

        then:
        target.files == [file]
        1 * listener.changed(file.path)
        0 * listener._
    }

    def "output snapshot detects a file change for a file that didn't exist originally"() {
        given:
        TestFile file
        snapshotBeforeAndAfterTasks({
            file = rootDir.createFile('file')
        }, {
            file.write('new content')
        })

        when:
        createOutputSnapshot()

        then:
        target.files == [file]
        1 * listener.added(file.path)
        0 * listener._
    }

    def "output snapshot detects that file's type has changed"() {
        given:
        TestFile file1 = rootDir.createFile('1')
        TestFile file2 = rootDir.createDir('2')

        snapshotBeforeAndAfterTasks(null) {
            file1.delete()
            file1.createDir()
            file2.deleteDir()
            file2.createFile()
        }

        when:
        createOutputSnapshot()

        then:
        target.files == [file2]
        1 * listener.changed(file1.path)
        1 * listener.changed(file2.path)
        0 * listener._
    }

    def "output snapshot detects that files have been deleted"() {
        given:
        TestFile file1 = rootDir.createFile('1')
        TestFile file2 = rootDir.createFile('2')

        snapshotBeforeAndAfterTasks(null) {
            file1.delete()
            file2.delete()
        }

        when:
        createOutputSnapshot()

        then:
        target.files.empty
        1 * listener.removed(file1.path)
        1 * listener.removed(file2.path)
        0 * listener._
    }

    def "output snapsnot ignores files created between snapshots"() {
        given:
        TestFile file1 = rootDir.createFile('1')
        snapshotBeforeAndAfterTasks({
            TestFile file2 = rootDir.createFile('2')
            TestFile file3 = rootDir.createDir('3')
            TestFile file4 = rootDir.file('4')
        }, null)

        when:
        createOutputSnapshot()

        then:
        target.files == [file1]
        0 * listener._
    }

    private void changes(FileCollectionSnapshot newSnapshot, FileCollectionSnapshot oldSnapshot, ChangeListener<String> listener) {
        changes(newSnapshot.iterateContentChangesSince(oldSnapshot, [] as Set), listener)
    }

    private void changes(FileCollectionSnapshot.ChangeIterator<String> changes, ChangeListener<String> listener) {
        while (changes.next(listener)) {
        }
    }

    private OutputFilesCollectionSnapshotter.OutputFilesSnapshot createInitialOutputSnapshot() {
        snapshotter.createOutputSnapshot(null, snapshotter.emptySnapshot(), createSnapshot(rootDir), files(rootDir))
    }

    private FileCollectionSnapshot snapshotFiles(File... filesToSnapshot) {
        snapshotter.snapshot(snapshotter.preCheck(files(filesToSnapshot), false))
    }

    private void snapshotBeforeAndAfterTasks(Closure betweenTasksClosure, Closure taskActionClosure) {
        previous = createInitialOutputSnapshot()
        if (betweenTasksClosure != null) {
            betweenTasksClosure()
        }
        before = createSnapshot(rootDir)
        if (taskActionClosure != null) {
            taskActionClosure()
        }
        after = createSnapshot(rootDir)
    }

    private void createOutputSnapshot() {
        target = snapshotter.createOutputSnapshot(previous, before, after, files(rootDir))
        changes(target, previous, listener)
    }

    private FileCollection files(File... files) {
        new SimpleFileCollection(files)
    }

    private FileCollectionSnapshot createSnapshot(File dir) {
        snapshotter.snapshot(snapshotter.preCheck(createFileCollection(dir), false))
    }

    private FileCollection createFileCollection(File dir) {
        TestFiles.fileCollectionFactory().fixed("root", dir)
    }
}
