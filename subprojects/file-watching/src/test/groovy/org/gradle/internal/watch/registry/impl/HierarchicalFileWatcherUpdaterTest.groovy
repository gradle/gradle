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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.nio.file.Paths

class HierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher) {
        new HierarchicalFileWatcherUpdater(watcher)
    }

    def "does not watch project root directory if no snapshot is inside"() {
        def projectRootDirectory = file("rootDir").createDir()
        def secondProjectRootDirectory = file("rootDir2").createDir()
        def fileInProjectRootDirectory = projectRootDirectory.file("some/path/file.txt").createFile()

        when:
        updater.updateRootProjectDirectories([projectRootDirectory, secondProjectRootDirectory])
        then:
        0 * _

        when:
        addSnapshot(snapshotRegularFile(fileInProjectRootDirectory))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [projectRootDirectory]) })
    }

    def "starts and stops watching project root directories"() {
        def projectRootDirectories = ["first", "second", "third"].collect { file(it).createDir() }
        def watchedDirsInsideProjectRootDirectories = addSnapshotsInProjectRootDirectories(projectRootDirectories)

        when:
        updater.updateRootProjectDirectories(projectRootDirectories)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, watchedDirsInsideProjectRootDirectories )})
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, projectRootDirectories) })
        0 * _

        when:
        invalidate(watchedDirsInsideProjectRootDirectories[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [projectRootDirectories[0]]) })
        0 * _

        when:
        updater.updateRootProjectDirectories(projectRootDirectories[1..2])
        then:
        0 * _

        when:
        addSnapshotsInProjectRootDirectories([projectRootDirectories[0]])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchedDirsInsideProjectRootDirectories[0]]) })
        0 * _
    }

    def "does not watch non-existing project root directories"() {
        def projectRootDirectories = ["first", "second"].collect { file(it).createDir() }
        def nonExistingProjectRootDirectory = file("third/non-existing")
        file("third").createDir()
        def watchedDirsInsideProjectRootDirectories = addSnapshotsInProjectRootDirectories(projectRootDirectories)

        when:
        updater.updateRootProjectDirectories(projectRootDirectories + nonExistingProjectRootDirectory)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, watchedDirsInsideProjectRootDirectories) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, projectRootDirectories) })
        0 * _

        when:
        addSnapshot(missingFileSnapshot(nonExistingProjectRootDirectory.file("some/missing/file.txt")))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [nonExistingProjectRootDirectory.parentFile]) })
        0 * _
    }

    def "can change the project root directories"() {
        def firstProjectRootDirectories = ["first", "second"].collect { file(it).createDir() }
        def secondProjectRootDirectories = ["second", "third"].collect { file(it).createDir() }
        def watchedDirsInsideFirstDir = addSnapshotsInProjectRootDirectories([file("first")])
        def watchedDirsInsideSecondDir = addSnapshotsInProjectRootDirectories([file("second")])
        def watchedDirsInsideThirdDir = addSnapshotsInProjectRootDirectories([file("third")])

        when:
        updater.updateRootProjectDirectories(firstProjectRootDirectories)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, watchedDirsInsideFirstDir + watchedDirsInsideSecondDir) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, firstProjectRootDirectories) })
        0 * _

        when:
        updater.buildFinished()
        then:
        0 * _

        when:
        updater.updateRootProjectDirectories(secondProjectRootDirectories)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, watchedDirsInsideThirdDir) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [file("third")]) })
        0 * _

        when:
        invalidate(watchedDirsInsideFirstDir[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        0 * _

        when:
        updater.buildFinished()
        then:
        0 * _

        when:
        addSnapshotsInProjectRootDirectories([file("first")])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, watchedDirsInsideFirstDir) })
        0 * _
    }

    def "only adds watches for the roots of project root directories"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()
        def watchedDirsInsideProjectRootDirectories = addSnapshotsInProjectRootDirectories([secondDir, directoryWithinFirst])

        when:
        updater.updateRootProjectDirectories([firstDir, directoryWithinFirst, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, watchedDirsInsideProjectRootDirectories) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir, secondDir]) })
        0 * _
    }

    def "starts watching project root directory which was beneath another project root directory"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()
        def watchedDirsInsideProjectRootDirectories = addSnapshotsInProjectRootDirectories([secondDir, directoryWithinFirst])

        when:
        updater.updateRootProjectDirectories([firstDir, directoryWithinFirst, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, watchedDirsInsideProjectRootDirectories) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir, secondDir]) })
        0 * _

        when:
        updater.buildFinished()
        then:
        0 * _

        when:
        updater.updateRootProjectDirectories([directoryWithinFirst, secondDir])
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
        def watchedDirsInsideProjectRootDirectories = addSnapshotsInProjectRootDirectories([secondDir, directoryWithinFirst])

        when:
        updater.updateRootProjectDirectories([directoryWithinFirst, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, watchedDirsInsideProjectRootDirectories) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryWithinFirst, secondDir]) })
        0 * _

        when:
        updater.updateRootProjectDirectories([firstDir, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir]) })
        0 * _
    }

    def "does not watch snapshot roots in project root directories"() {
        def rootDir = file("root").createDir()
        updater.updateRootProjectDirectories([rootDir])
        def subDirInRootDir = rootDir.file("some/path").createDir()
        def snapshotInRootDir = snapshotDirectory(subDirInRootDir)

        when:
        addSnapshot(snapshotInRootDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        updater.buildFinished()
        then:
        0 * _

        when:
        updater.updateRootProjectDirectories([])
        then:
        0 * _

        when:
        invalidate(snapshotInRootDir)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        addSnapshot(snapshotInRootDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, ([rootDir])) })
        0 * _
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _
    }

    def "starts watching closer parent when missing file is created"() {
        def rootDir = file("root").createDir()
        def missingFile = rootDir.file("a/b/c/missing.txt")

        when:
        addSnapshot(missingFileSnapshot(missingFile))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        missingFile.createFile()
        addSnapshot(snapshotRegularFile(missingFile))
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [missingFile.parentFile]) })
        0 * _
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "resolves recursive UNIX roots #directories to #resolvedRoots"() {
        expect:
        resolveRecursiveRoots(directories) == resolvedRoots

        where:
        directories        | resolvedRoots
        []                 | []
        ["/a"]             | ["/a"]
        ["/a", "/b"]       | ["/a", "/b"]
        ["/a", "/a/b"]     | ["/a"]
        ["/a/b", "/a"]     | ["/a"]
        ["/a", "/a/b/c/d"] | ["/a"]
        ["/a/b/c/d", "/a"] | ["/a"]
        ["/a", "/b/a"]     | ["/a", "/b/a"]
        ["/b/a", "/a"]     | ["/a", "/b/a"]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "resolves recursive Windows roots #directories to #resolvedRoots"() {
        expect:
        resolveRecursiveRoots(directories) == resolvedRoots

        where:
        directories                 | resolvedRoots
        []                          | []
        ["C:\\a"]                   | ["C:\\a"]
        ["C:\\a", "C:\\b"]          | ["C:\\a", "C:\\b"]
        ["C:\\a", "C:\\a\\b"]       | ["C:\\a"]
        ["C:\\a\\b", "C:\\a"]       | ["C:\\a"]
        ["C:\\a", "C:\\a\\b\\c\\d"] | ["C:\\a"]
        ["C:\\a\\b\\c\\d", "C:\\a"] | ["C:\\a"]
        ["C:\\a", "C:\\b\\a"]       | ["C:\\a", "C:\\b\\a"]
        ["C:\\b\\a", "C:\\a"]       | ["C:\\a", "C:\\b\\a"]
    }

    MissingFileSnapshot missingFileSnapshot(File location) {
        new MissingFileSnapshot(location.getAbsolutePath(), AccessType.DIRECT)
    }

    private List<TestFile> addSnapshotsInProjectRootDirectories(Collection<TestFile> projectRootDirectories) {
        projectRootDirectories.collect { projectRootDirectory ->
            def fileInside = projectRootDirectory.file("some/subdir/file.txt").createFile()
            addSnapshot(snapshotRegularFile(fileInside))
            return fileInside.parentFile
        }
    }

    private static List<String> resolveRecursiveRoots(List<String> directories) {
        HierarchicalFileWatcherUpdater.resolveHierarchiesToWatch(directories.collect { Paths.get(it) } as Set)
            .collect { it.toString() }
            .sort()
    }
}
