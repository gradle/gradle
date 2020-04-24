/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.vfs

import net.rubygrapefruit.platform.file.FileWatcher
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.impl.DirectorySnapshotter
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy
import org.gradle.internal.vfs.impl.DelegatingDiffCapturingUpdateFunctionDecorator
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicBoolean

@CleanupTestDirectory
class HierarchicalFileWatcherUpdaterTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def watcher = Mock(FileWatcher)
    def updater = new HierarchicalFileWatcherUpdater(watcher)
    def directorySnapshotter = new DirectorySnapshotter(TestFiles.fileHasher(), new StringInterner())
    def decorator = new DelegatingDiffCapturingUpdateFunctionDecorator({ String path -> true})
    def root = DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)

    def setup() {
        decorator.setSnapshotDiffListener(updater) { currentRoot, runnable ->
            runnable.run()
            return currentRoot
        }
    }

    def "starts and stops watching must watch directories"() {
        def mustWatchDirectories = ["first", "second", "third"].collect { temporaryFolder.testDirectory.createDir(it)} as Set

        when:
        updater.updateMustWatchDirectories(mustWatchDirectories)
        then:
        1 * watcher.startWatching({ it as Set == mustWatchDirectories })
        0 * _

        when:
        updater.updateMustWatchDirectories([])
        then:
        1 * watcher.stopWatching({ it as Set == mustWatchDirectories })
        0 * _
    }

    def "only adds watches for the roots of must watch directories"() {
        def mustWatchDirectoryRoots = ["first", "second"].collect { temporaryFolder.testDirectory.createDir(it)} as Set
        def mustWatchDirectories = mustWatchDirectoryRoots + temporaryFolder.testDirectory.createDir("first/within")

        when:
        updater.updateMustWatchDirectories(mustWatchDirectories)
        then:
        1 * watcher.startWatching({ it as Set == mustWatchDirectoryRoots })
        0 * _
    }

    def "does not watch non-existing must watch directories"() {
        def existingMustWatchDirectories = ["first", "second"].collect { temporaryFolder.testDirectory.createDir(it)} as Set
        def nonExistingMustWatchDirectory = temporaryFolder.testDirectory.file("non-existing")

        when:
        updater.updateMustWatchDirectories(existingMustWatchDirectories + nonExistingMustWatchDirectory)
        then:
        1 * watcher.startWatching({ it as Set == existingMustWatchDirectories })
        0 * _
    }

    def "can change the must watch directories"() {
        def firstMustWatchDirectories = ["first", "second"].collect { temporaryFolder.testDirectory.createDir(it)} as Set
        def secondMustWatchDirectories = ["second", "third"].collect { temporaryFolder.testDirectory.createDir(it)} as Set

        when:
        updater.updateMustWatchDirectories(firstMustWatchDirectories)
        then:
        1 * watcher.startWatching({ it as Set == firstMustWatchDirectories })
        0 * _

        when:
        updater.updateMustWatchDirectories(secondMustWatchDirectories)
        then:
        1 * watcher.stopWatching([temporaryFolder.testDirectory.file("first")])
        then:
        1 * watcher.startWatching([temporaryFolder.testDirectory.file("third")])
        0 * _
    }

    def "does not watch snapshot roots in must watch directories"() {
        def rootDir = file("root").createDir()
        updater.updateMustWatchDirectories([rootDir])
        def subDirInRootDir = rootDir.file("some/path").createDir()
        def snapshotInRootDir = snapshotDirectory(subDirInRootDir)

        when:
        addSnapshot(snapshotInRootDir)
        then:
        0 * _

        when:
        updater.updateMustWatchDirectories([])
        then:
        1 * watcher.stopWatching({ it == [rootDir]})
        then:
        1 * watcher.startWatching({ it as Set == ([subDirInRootDir.parentFile] as Set) })
        0 * _
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ it == [rootDir.parentFile] })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        1 * watcher.stopWatching({ it == [rootDir.parentFile] })
        then:
        1 * watcher.startWatching({ it == [rootDir] })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching([rootDir])
        0 * _
    }

    TestFile file(Object... path) {
        temporaryFolder.testDirectory.file(path)
    }

    CompleteDirectorySnapshot snapshotDirectory(File directory) {
        directorySnapshotter.snapshot(directory.absolutePath, null, new AtomicBoolean(false)) as CompleteDirectorySnapshot
    }

    void addSnapshot(CompleteFileSystemLocationSnapshot snapshot) {
        root = decorator.decorate({ currentRoot, listener -> currentRoot.store(snapshot.absolutePath, snapshot, listener) }).updateRoot(root)
    }

    void invalidate(String absolutePath) {
        root = decorator.decorate({ currentRoot, listener -> currentRoot.invalidate(absolutePath, listener) }).updateRoot(root)
    }

    void invalidate(CompleteFileSystemLocationSnapshot snapshot) {
        invalidate(snapshot.absolutePath)
    }

    RegularFileSnapshot snapshotRegularFile(File regularFile) {
        new RegularFileSnapshot(regularFile.absolutePath, regularFile.name, TestFiles.fileHasher().hash(regularFile), FileMetadata.from(Files.readAttributes(regularFile.toPath(), BasicFileAttributes)))
    }
}
