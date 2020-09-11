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

import net.rubygrapefruit.platform.file.FileWatcher
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.watch.registry.FileWatcherUpdater
import org.gradle.test.fixtures.file.TestFile

import java.util.function.Predicate

import static org.gradle.internal.watch.registry.impl.HierarchicalFileWatcherUpdater.FileSystemLocationToWatchValidator.NO_VALIDATION

class HierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher, Predicate<String> watchFilter) {
        new HierarchicalFileWatcherUpdater(watcher, NO_VALIDATION, watchFilter)
    }

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
        def snapshotsInsideWatchableHierarchies = addSnapshotsInWatchableHierarchies(watchableHierarchies)
        then:
        watchableHierarchies.each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
        0 * _

        when:
        invalidate(snapshotsInsideWatchableHierarchies[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [watchableHierarchies[0]]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        addSnapshotsInWatchableHierarchies([watchableHierarchies[0]])
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
        addSnapshotsInWatchableHierarchies(watchableHierarchies)
        then:
        watchableHierarchies.each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
        0 * _

        when:
        addSnapshot(missingFileSnapshot(nonExistingWatchableHierarchy.file("some/missing/file.txt")))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [nonExistingWatchableHierarchy.parentFile]) })
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
        def snapshotsInsideFirstWatchableHierarchy = addSnapshotsInWatchableHierarchies([file("first")])
        addSnapshotsInWatchableHierarchies([file("second")])
        addSnapshotsInWatchableHierarchies([thirdWatchableHierarchy])
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
        addSnapshotsInWatchableHierarchies([thirdWatchableHierarchy])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [thirdWatchableHierarchy]) })
        0 * _

        when:
        invalidate(snapshotsInsideFirstWatchableHierarchy[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        addSnapshotsInWatchableHierarchies([file("first")])
        then:
        0 * _
    }

    def "watch only outermost hierarchy"() {
        def outerDir = file("outer").createDir()
        def innerDirBefore = file("outer/inner1").createDir()
        def innerDirAfter = file("outer/inner2").createDir()

        when:
        registerWatchableHierarchies([innerDirBefore, outerDir, innerDirAfter])
        addSnapshotsInWatchableHierarchies([outerDir, innerDirAfter, innerDirBefore])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [outerDir]) })
        0 * _
    }

    def "starts watching hierarchy to watch which was beneath another hierarchy to watch"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        registerWatchableHierarchies([directoryWithinFirst, firstDir, secondDir])
        addSnapshotsInWatchableHierarchies([secondDir, directoryWithinFirst])
        then:
        [firstDir, secondDir].each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        registerWatchableHierarchies([directoryWithinFirst, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [firstDir]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        0 * _
    }

    def "stops watching project root directory which is now beneath another project root directory"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        registerWatchableHierarchies([directoryWithinFirst, secondDir])
        addSnapshotsInWatchableHierarchies([secondDir, directoryWithinFirst])
        then:
        [directoryWithinFirst, secondDir].each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
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

    def "starts watching closer parent when missing file is created"() {
        def rootDir = file("root").createDir()
        def watchableHierarchy = rootDir.file("a/b/projectDir")
        def missingFile = watchableHierarchy.file("c/missing.txt")

        when:
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(missingFileSnapshot(missingFile))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        missingFile.createFile()
        addSnapshot(snapshotRegularFile(missingFile))
        buildFinished()
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        0 * _
    }

    MissingFileSnapshot missingFileSnapshot(File location) {
        new MissingFileSnapshot(location.getAbsolutePath(), AccessType.DIRECT)
    }

    private List<TestFile> addSnapshotsInWatchableHierarchies(Collection<TestFile> projectRootDirectories) {
        projectRootDirectories.collect { projectRootDirectory ->
            def fileInside = projectRootDirectory.file("some/subdir/file.txt").createFile()
            addSnapshot(snapshotRegularFile(fileInside))
            return fileInside.parentFile
        }
    }
}
