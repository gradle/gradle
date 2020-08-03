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
import java.util.function.Predicate

import static org.gradle.internal.watch.registry.impl.HierarchicalFileWatcherUpdater.FileSystemLocationToWatchValidator.NO_VALIDATION

class HierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher, Predicate<String> watchFilter) {
        new HierarchicalFileWatcherUpdater(watcher, NO_VALIDATION, watchFilter)
    }

    def "does not watch project root directory if no snapshot is inside"() {
        def projectRootDirectory = file("rootDir").createDir()
        def secondProjectRootDirectory = file("rootDir2").createDir()
        def fileInProjectRootDirectory = projectRootDirectory.file("some/path/file.txt").createFile()

        when:
        updater.discoveredHierarchyToWatch(projectRootDirectory, virtualFileSystem.getRoot())
        updater.discoveredHierarchyToWatch(secondProjectRootDirectory, virtualFileSystem.getRoot())
        then:
        0 * _

        when:
        addSnapshot(snapshotRegularFile(fileInProjectRootDirectory))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [projectRootDirectory]) })
    }

    def "starts and stops watching project root directories"() {
        def projectRootDirectories = ["first", "second", "third"].collect { file(it).createDir() }

        when:
        projectRootDirectories.each { updater.discoveredHierarchyToWatch(it, virtualFileSystem.getRoot()) }
        then:
        0 * _

        when:
        def watchedDirsInsideProjectRootDirectories = addSnapshotsInProjectRootDirectories(projectRootDirectories)
        then:
        projectRootDirectories.each { projectRootDirectory ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [projectRootDirectory]) })
        }
        0 * _

        when:
        invalidate(watchedDirsInsideProjectRootDirectories[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [projectRootDirectories[0]]) })
        0 * _

        when:
        virtualFileSystem.update { root, nodeDiffListener ->
            updater.buildFinished(root)
        }
        then:
        _ * watchFilter.test(_) >> true
        0 * _

        when:
        addSnapshotsInProjectRootDirectories([projectRootDirectories[0]])
        then:
        vfsHasSnapshotsAt(projectRootDirectories[0])
        0 * _

        when:
        virtualFileSystem.update { root, nodeDiffListener ->
            updater.buildFinished(root)
        }
        then:
        !vfsHasSnapshotsAt(projectRootDirectories[0])
        _ * watchFilter.test(_) >> true
        0 * _
    }

    def "does not watch non-existing project root directories"() {
        def projectRootDirectories = ["first", "second"].collect { file(it).createDir() }
        def nonExistingProjectRootDirectory = file("third/non-existing")
        file("third").createDir()

        when:
        virtualFileSystem.update { root, diffListener ->
            projectRootDirectories.each { updater.discoveredHierarchyToWatch(it, root) }
            updater.discoveredHierarchyToWatch(nonExistingProjectRootDirectory, root)
            return root
        }
        then:
        0 * _

        when:
        addSnapshotsInProjectRootDirectories(projectRootDirectories)
        then:
        projectRootDirectories.each { projectDirectory ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [projectDirectory]) })
        }
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
        def thirdProjectRootDirectory = file("third")

        when:
        discoverHierarchiesToWatch(firstProjectRootDirectories)
        then:
        0 * _

        when:
        def watchedDirsInsideFirstDir = addSnapshotsInProjectRootDirectories([file("first")])
        addSnapshotsInProjectRootDirectories([file("second")])
        addSnapshotsInProjectRootDirectories([thirdProjectRootDirectory])
        then:
        firstProjectRootDirectories.each { projectRootDirectory ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [projectRootDirectory]) })
        }
        0 * _
        vfsHasSnapshotsAt(thirdProjectRootDirectory)

        when:
        virtualFileSystem.update { root, diffListener ->
            updater.buildFinished(root)
        }
        then:
        _ * watchFilter.test(_) >> true
        0 * _
        !vfsHasSnapshotsAt(thirdProjectRootDirectory)

        when:
        discoverHierarchiesToWatch(secondProjectRootDirectories)
        addSnapshotsInProjectRootDirectories([thirdProjectRootDirectory])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [thirdProjectRootDirectory]) })
        0 * _

        when:
        invalidate(watchedDirsInsideFirstDir[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        0 * _

        when:
        virtualFileSystem.update { root, diffListener ->
            updater.buildFinished(root)
        }
        then:
        _ * watchFilter.test(_) >> true
        0 * _

        when:
        addSnapshotsInProjectRootDirectories([file("first")])
        then:
        0 * _
    }

    def "only adds watches for the roots of project root directories"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        discoverHierarchiesToWatch([firstDir, directoryWithinFirst, secondDir])
        addSnapshotsInProjectRootDirectories([secondDir, directoryWithinFirst])
        then:
        [firstDir, secondDir].each { directoryToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryToWatch]) })
        }
        0 * _
    }

    def "starts watching project root directory which was beneath another project root directory"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        discoverHierarchiesToWatch([firstDir, directoryWithinFirst, secondDir])
        addSnapshotsInProjectRootDirectories([secondDir, directoryWithinFirst])
        then:
        [firstDir, secondDir].each { directoryToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryToWatch]) })
        }
        0 * _

        when:
        virtualFileSystem.update { root, diffListener ->
            updater.buildFinished(root)
        }
        then:
        _ * watchFilter.test(_) >> true
        0 * _

        when:
        discoverHierarchiesToWatch([directoryWithinFirst, secondDir])
        then:
        // TODO: Tighten the watching here by only watch directories when necessary
//        1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        0 * _
    }

    def "stops watching project root directory which is now beneath another project root directory"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        discoverHierarchiesToWatch([directoryWithinFirst, secondDir])
        addSnapshotsInProjectRootDirectories([secondDir, directoryWithinFirst])
        then:
        [directoryWithinFirst, secondDir].each { directoryToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryToWatch]) })
        }
        0 * _

        when:
        discoverHierarchiesToWatch([firstDir, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir]) })
        0 * _
    }

    def "does not watch snapshot roots in project root directories"() {
        def rootDir = file("root").createDir()
        discoverHierarchiesToWatch([rootDir])
        def subDirInRootDir = rootDir.file("some/path").createDir()
        def snapshotInRootDir = snapshotDirectory(subDirInRootDir)

        when:
        addSnapshot(snapshotInRootDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        virtualFileSystem.update { root, diffListener ->
            updater.buildFinished(root)
        }
        then:
        _ * watchFilter.test(_)
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
        discoverHierarchiesToWatch([rootDir.parentFile])
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
        def projectDir = rootDir.file("a/b/projectDir")
        def missingFile = projectDir.file("c/missing.txt")

        when:
        updater.discoveredHierarchyToWatch(projectDir, virtualFileSystem.root)
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
        1 * watcher.startWatching({ equalIgnoringOrder(it, [projectDir]) })
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

    private void discoverHierarchiesToWatch(Iterable<File> hierarchiesToWatch) {
        hierarchiesToWatch.each { hierarchyToWatch ->
            virtualFileSystem.update { root, diffListener ->
                updater.discoveredHierarchyToWatch(hierarchyToWatch, root)
                return root
            }
        }
    }
}
