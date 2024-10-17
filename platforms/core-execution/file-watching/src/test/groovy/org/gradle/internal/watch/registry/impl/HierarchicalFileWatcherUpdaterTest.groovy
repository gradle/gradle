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
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.watch.registry.FileWatcherUpdater

import static org.gradle.internal.watch.registry.impl.HierarchicalFileWatcherUpdater.FileSystemLocationToWatchValidator.NO_VALIDATION

class HierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher, WatchableHierarchies watchableHierarchies) {
        new HierarchicalFileWatcherUpdater(watcher, NO_VALIDATION, probeRegistry, watchableHierarchies, movedWatchedDirectoriesSupplier)
    }

    @Override
    int getIfNonHierarchical() { 0 }

    def "does not watch hierarchy to watch if no snapshot is inside"() {
        def watchableHierarchy = file("watchable").createDir()
        def secondWatchableHierarchy = file("watchable2").createDir()
        def fileInWatchableHierarchy = watchableHierarchy.file("some/path/file.txt").createFile()

        when:
        registerWatchableHierarchies([watchableHierarchy, secondWatchableHierarchy])
        then:
        0 * _

        when:
        addSnapshot(snapshotRegularFile(fileInWatchableHierarchy))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
    }

    def "starts and stops watching hierarchies to watch"() {
        def watchableHierarchies = ["first", "second", "third"].collect { file(it).createDir() }

        when:
        registerWatchableHierarchies(watchableHierarchies)
        then:
        0 * _

        when:
        def snapshotInsideFirstWatchableHierarchy = addSnapshotInWatchableHierarchy(file("first"))
        addSnapshotInWatchableHierarchy(file("second"))
        addSnapshotInWatchableHierarchy(file("third"))
        then:
        watchableHierarchies.each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
        0 * _

        when:
        invalidate(snapshotInsideFirstWatchableHierarchy.absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [watchableHierarchies[0]]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        addSnapshotInWatchableHierarchy(watchableHierarchies[0])
        then:
        vfsHasSnapshotsAt(watchableHierarchies[0])
        0 * _

        when:
        buildFinished()
        then:
        !vfsHasSnapshotsAt(watchableHierarchies[0])
        0 * _
    }

    def "does not watch non-existing hierarchies to watch"() {
        def watchableHierarchies = ["first", "second"].collect { file(it).createDir() }
        def nonExistingWatchableHierarchy = file("third").createDir().file("non-existing")

        when:
        registerWatchableHierarchies(watchableHierarchies + nonExistingWatchableHierarchy)
        then:
        0 * _

        when:
        watchableHierarchies.each { addSnapshotInWatchableHierarchy(it) }
        then:
        watchableHierarchies.each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
        0 * _

        when:
        addSnapshot(missingFileSnapshot(nonExistingWatchableHierarchy.file("some/missing/file.txt")))
        then:
        0 * _
    }

    def "can change the hierarchies to watch"() {
        def firstSetOfWatchableHierarchies = ["first", "second"].collect { file(it).createDir() }
        def secondSetOfWatchableHierarchies = ["second", "third"].collect { file(it).createDir() }
        def thirdWatchableHierarchy = file("third")

        when:
        registerWatchableHierarchies(firstSetOfWatchableHierarchies)
        then:
        0 * _

        when:
        def snapshotInsideFirstWatchableHierarchy = addSnapshotInWatchableHierarchy(file("first"))
        addSnapshotInWatchableHierarchy(file("second"))
        addSnapshotInWatchableHierarchy(thirdWatchableHierarchy)
        then:
        firstSetOfWatchableHierarchies.each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
        0 * _
        vfsHasSnapshotsAt(thirdWatchableHierarchy)

        when:
        buildFinished()
        then:
        0 * _
        !vfsHasSnapshotsAt(thirdWatchableHierarchy)

        when:
        registerWatchableHierarchies(secondSetOfWatchableHierarchies)
        addSnapshotInWatchableHierarchy(thirdWatchableHierarchy)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [thirdWatchableHierarchy]) })
        0 * _

        when:
        invalidate(snapshotInsideFirstWatchableHierarchy.absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        addSnapshotInWatchableHierarchy(file("first"))
        then:
        0 * _
    }

    def "watch only outermost hierarchy"() {
        def outerDir = file("outer").createDir()
        def innerDirBefore = file("outer/inner1").createDir()
        def innerDirAfter = file("outer/inner2").createDir()

        registerWatchableHierarchies([innerDirBefore, outerDir, innerDirAfter])

        when:
        addSnapshotInWatchableHierarchy(outerDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [outerDir]) })
        0 * _

        when:
        addSnapshotInWatchableHierarchy(innerDirAfter)
        then:
        0 * _

        when:
        addSnapshotInWatchableHierarchy(innerDirBefore)
        then:
        0 * _
    }

    def "keeps watching outermost hierarchy until there is no content left"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        registerWatchableHierarchies([directoryWithinFirst, firstDir, secondDir])

        when:
        addSnapshotInWatchableHierarchy(secondDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [secondDir]) })
        0 * _

        when:
        def snapshot = addSnapshotInWatchableHierarchy(directoryWithinFirst)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir]) })
        0 * _

        when:
        updater.triggerWatchProbe(watchProbeFor(secondDir).absolutePath)
        updater.triggerWatchProbe(watchProbeFor(firstDir).absolutePath)
        updater.triggerWatchProbe(watchProbeFor(directoryWithinFirst).absolutePath)
        then:
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        buildStarted()
        then:
        0 * _

        when:
        registerWatchableHierarchies([directoryWithinFirst, secondDir])
        then:
        0 * _

        when:
        invalidate(snapshot.absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [firstDir]) })
        0 * _
    }

    def "stops watching project root directory which is now beneath another project root directory"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        registerWatchableHierarchies([directoryWithinFirst, secondDir])

        when:
        addSnapshotInWatchableHierarchy(secondDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [secondDir]) })
        0 * _

        when:
        addSnapshotInWatchableHierarchy(directoryWithinFirst)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        0 * _

        when:
        registerWatchableHierarchies([firstDir, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir]) })
        0 * _
    }

    def "does not watch snapshot roots in hierarchies to watch"() {
        def watchableHierarchy = file("watchable").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        def subDirInRootDir = watchableHierarchy.file("some/path").createDir()
        def snapshotInRootDir = snapshotDirectory(subDirInRootDir)

        when:
        addSnapshot(snapshotInRootDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        invalidate(snapshotInRootDir)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        0 * _

        when:
        addSnapshot(snapshotInRootDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, ([watchableHierarchy])) })
        0 * _
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        registerWatchableHierarchies([rootDir.parentFile])
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        0 * _
    }

    def "starts watching when missing file is created"() {
        def rootDir = file("root").createDir()
        def watchableHierarchy = rootDir.file("a/b/projectDir")
        def missingFile = watchableHierarchy.file("c/missing.txt")

        when:
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(missingFileSnapshot(missingFile))
        then:
        0 * _

        when:
        missingFile.createFile()
        addSnapshot(snapshotRegularFile(missingFile))
        buildFinished()
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
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
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        0 * _

        when:
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        0 * _

        when:
        buildFinished(Integer.MAX_VALUE, [unsupportedFileSystemMountPoint])
        then:
        vfsHasSnapshotsAt(watchableContent)
        !vfsHasSnapshotsAt(unwatchableContent)
        0 * _
    }

    private static MissingFileSnapshot missingFileSnapshot(File location) {
        new MissingFileSnapshot(location.getAbsolutePath(), AccessType.DIRECT)
    }
}
