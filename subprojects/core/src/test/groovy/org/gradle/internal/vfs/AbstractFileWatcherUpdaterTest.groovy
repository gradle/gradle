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
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.snapshot.impl.DirectorySnapshotter
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy
import org.gradle.internal.vfs.impl.DelegatingDiffCapturingUpdateFunctionDecorator
import org.gradle.internal.vfs.watch.FileWatcherUpdater
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicBoolean

@CleanupTestDirectory
abstract class AbstractFileWatcherUpdaterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def watcher = Mock(FileWatcher)
    def directorySnapshotter = new DirectorySnapshotter(TestFiles.fileHasher(), new StringInterner())
    def decorator = new DelegatingDiffCapturingUpdateFunctionDecorator({ String path -> true})
    def root = DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)

    def updater

    def setup() {
        updater = createUpdater(watcher)
        decorator.setSnapshotDiffListener(updater) { SnapshotHierarchy currentRoot, Runnable runnable ->
            runnable.run()
            return currentRoot
        }
    }

    abstract FileWatcherUpdater createUpdater(FileWatcher watcher)

    def "starts and stops watching must watch directories"() {
        def mustWatchDirectories = ["first", "second", "third"].collect { file(it).createDir() }

        when:
        updater.updateMustWatchDirectories(mustWatchDirectories)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, mustWatchDirectories) })
        0 * _

        when:
        updater.updateMustWatchDirectories([])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, mustWatchDirectories) })
        0 * _
    }

    def "does not watch non-existing must watch directories"() {
        def existingMustWatchDirectories = ["first", "second"].collect { file(it).createDir() }
        def nonExistingMustWatchDirectory = file("non-existing")

        when:
        updater.updateMustWatchDirectories(existingMustWatchDirectories + nonExistingMustWatchDirectory)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, existingMustWatchDirectories) })
        0 * _
    }

    def "can change the must watch directories"() {
        def firstMustWatchDirectories = ["first", "second"].collect { file(it).createDir() }
        def secondMustWatchDirectories = ["second", "third"].collect { file(it).createDir() }

        when:
        updater.updateMustWatchDirectories(firstMustWatchDirectories)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, firstMustWatchDirectories) })
        0 * _

        when:
        updater.updateMustWatchDirectories(secondMustWatchDirectories)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [file("third")]) })
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

    static RegularFileSnapshot snapshotRegularFile(File regularFile) {
        new RegularFileSnapshot(regularFile.absolutePath, regularFile.name, TestFiles.fileHasher().hash(regularFile), FileMetadata.from(Files.readAttributes(regularFile.toPath(), BasicFileAttributes)))
    }

    static boolean equalIgnoringOrder(Object actual, Collection<?> expected) {
        List<?> actualSorted = (actual as List).toSorted()
        List<?> expectedSorted = (expected as List).toSorted()
        return actualSorted == expectedSorted
    }
}
