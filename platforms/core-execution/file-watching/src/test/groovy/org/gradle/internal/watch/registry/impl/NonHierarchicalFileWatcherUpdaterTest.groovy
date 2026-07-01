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

package org.gradle.internal.watch.registry.impl

import org.gradle.fileevents.FileWatcher
import org.gradle.internal.watch.registry.FileWatcherUpdater

class NonHierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher, WatchableHierarchies watchableHierarchies) {
        new NonHierarchicalFileWatcherUpdater(watcher, probeRegistry, watchableHierarchies, movedWatchedDirectoriesSupplier)
    }

    @Override
    int getIfNonHierarchical() { 1 }

    def "only watches directories in hierarchies to watch"() {
        def watchableHierarchies = ["first", "second", "third"].collect { file(it).createDir() }
        def fileInWatchableHierarchies = file("first/inside/root/dir/file.txt")
        def fileOutsideOfWatchableHierarchies = file("forth").file("someFile.txt")

        when:
        registerWatchableHierarchies(watchableHierarchies)
        then:
        0 * _

        when:
        fileInWatchableHierarchies.createFile()
        addSnapshot(snapshotRegularFile(fileInWatchableHierarchies))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(file("first"))]) })
        1 * watcher.startWatching({ equalIgnoringOrder(it, [fileInWatchableHierarchies.parentFile]) })
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchies[0]]) })
        0 * _

        when:
        fileOutsideOfWatchableHierarchies.createFile()
        addSnapshot(snapshotRegularFile(fileOutsideOfWatchableHierarchies))
        then:
        0 * _
        vfsHasSnapshotsAt(fileOutsideOfWatchableHierarchies)

        when:
        buildFinished()
        then:
        0 * _
        !vfsHasSnapshotsAt(fileOutsideOfWatchableHierarchies)
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        registerWatchableHierarchies([rootDir])
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        1 * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(rootDir)]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(rootDir)]) })
        0 * _
    }

    def "deduplicates watches for symlinked directories resolving to the same path"() {
        // Reproduces https://github.com/gradle/gradle/issues/27940
        // When an included build has a symlink (e.g. build-logic/conventions/gradle -> ../../gradle),
        // both the real path and the symlink path may be requested for watching.
        // The watcher should canonicalize paths so the same physical directory is only watched once.
        def rootDir = file("root").createDir()
        def realDir = rootDir.file("real-dir").createDir()
        realDir.file("file.txt").createFile()

        // Create a symlink that points to the same directory
        def symlinkDir = rootDir.file("link-dir")
        symlinkDir.createLink(realDir)

        when:
        registerWatchableHierarchies([rootDir])
        then:
        0 * _

        when:
        // Add a snapshot via the real path
        addSnapshot(snapshotRegularFile(realDir.file("file.txt")))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(rootDir)]) })
        1 * watcher.startWatching({ equalIgnoringOrder(it, [realDir]) })
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        // Add a snapshot via the symlink path - this should NOT trigger another startWatching
        // for the same physical directory, since it canonicalizes to realDir
        def fileViaSymlink = new File(symlinkDir, "file.txt")
        addSnapshot(snapshotRegularFile(fileViaSymlink))
        then:
        // No additional startWatching calls because the canonical path is already watched
        0 * _
    }

    def "removes content on unsupported file systems at the end of the build"() {
        def watchableHierarchy = file("watchable").createDir()
        def watchableContent = watchableHierarchy.file("some/dir/file.txt").createFile()
        def unsupportedFileSystemMountPoint = watchableHierarchy.file("unsupported")
        def unwatchableContent = unsupportedFileSystemMountPoint.file("some/file.txt").createFile()

        when:
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotRegularFile(watchableContent))
        then:
        vfsHasSnapshotsAt(watchableContent)
        1 * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(watchableHierarchy)]) })
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableContent.parentFile]) })
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        0 * _

        when:
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.startWatching({ equalIgnoringOrder(it, [unwatchableContent.parentFile]) })
        0 * _

        when:
        buildFinished(Integer.MAX_VALUE,  [unsupportedFileSystemMountPoint])
        then:
        vfsHasSnapshotsAt(watchableContent)
        !vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [unwatchableContent.parentFile]) })
        0 * _
    }
}
