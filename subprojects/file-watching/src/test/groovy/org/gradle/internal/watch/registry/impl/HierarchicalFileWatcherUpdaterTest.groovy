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

    def "does not watch hierarchy to watch if no snapshot is inside"() {
        def hierarchyToWatch = file("rootDir").createDir()
        def secondHierarchyToWatch = file("rootDir2").createDir()
        def fileInHierarchyToWatch = hierarchyToWatch.file("some/path/file.txt").createFile()

        when:
        registerHierarchiesToWatch([hierarchyToWatch, secondHierarchyToWatch])
        then:
        0 * _

        when:
        addSnapshot(snapshotRegularFile(fileInHierarchyToWatch))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
    }

    def "starts and stops watching hierarchies to watch"() {
        def hierarchiesToWatch = ["first", "second", "third"].collect { file(it).createDir() }

        when:
        registerHierarchiesToWatch(hierarchiesToWatch)
        then:
        0 * _

        when:
        def snapshotsInsideHierarchiesToWatch = addSnapshotsInHierarchiesToWatch(hierarchiesToWatch)
        then:
        hierarchiesToWatch.each { hierarchyToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        }
        0 * _

        when:
        invalidate(snapshotsInsideHierarchiesToWatch[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [hierarchiesToWatch[0]]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        addSnapshotsInHierarchiesToWatch([hierarchiesToWatch[0]])
        then:
        vfsHasSnapshotsAt(hierarchiesToWatch[0])
        0 * _

        when:
        buildFinished()
        then:
        !vfsHasSnapshotsAt(hierarchiesToWatch[0])
        0 * _
    }

    def "does not watch non-existing hierarchies to watch"() {
        def hierarchiesToWatch = ["first", "second"].collect { file(it).createDir() }
        def nonExistingHierarchyToWatch = file("third").createDir().file("non-existing")

        when:
        registerHierarchiesToWatch(hierarchiesToWatch + nonExistingHierarchyToWatch)
        then:
        0 * _

        when:
        addSnapshotsInHierarchiesToWatch(hierarchiesToWatch)
        then:
        hierarchiesToWatch.each { hierarchyToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        }
        0 * _

        when:
        addSnapshot(missingFileSnapshot(nonExistingHierarchyToWatch.file("some/missing/file.txt")))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [nonExistingHierarchyToWatch.parentFile]) })
        0 * _
    }

    def "can change the hierarchies to watch"() {
        def firstSetOfHierarchiesToWatch = ["first", "second"].collect { file(it).createDir() }
        def secondSetOfHierarchiesToWatch = ["second", "third"].collect { file(it).createDir() }
        def thirdHierarchyToWatch = file("third")

        when:
        registerHierarchiesToWatch(firstSetOfHierarchiesToWatch)
        then:
        0 * _

        when:
        def snapshotsInsideFirstHierarchyToWatch = addSnapshotsInHierarchiesToWatch([file("first")])
        addSnapshotsInHierarchiesToWatch([file("second")])
        addSnapshotsInHierarchiesToWatch([thirdHierarchyToWatch])
        then:
        firstSetOfHierarchiesToWatch.each { hierarchyToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        }
        0 * _
        vfsHasSnapshotsAt(thirdHierarchyToWatch)

        when:
        buildFinished()
        then:
        0 * _
        !vfsHasSnapshotsAt(thirdHierarchyToWatch)

        when:
        registerHierarchiesToWatch(secondSetOfHierarchiesToWatch)
        addSnapshotsInHierarchiesToWatch([thirdHierarchyToWatch])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [thirdHierarchyToWatch]) })
        0 * _

        when:
        invalidate(snapshotsInsideFirstHierarchyToWatch[0].absolutePath)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        addSnapshotsInHierarchiesToWatch([file("first")])
        then:
        0 * _
    }

    def "only adds watches for the roots of the hierarchies to watch"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        registerHierarchiesToWatch([firstDir, directoryWithinFirst, secondDir])
        addSnapshotsInHierarchiesToWatch([secondDir, directoryWithinFirst])
        then:
        [firstDir, secondDir].each { hierarchyToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        }
        0 * _
    }

    def "starts watching hierarchy to watch which was beneath another hierarchy to watch"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        registerHierarchiesToWatch([firstDir, directoryWithinFirst, secondDir])
        addSnapshotsInHierarchiesToWatch([secondDir, directoryWithinFirst])
        then:
        [firstDir, secondDir].each { hierarchyToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        }
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        registerHierarchiesToWatch([directoryWithinFirst, secondDir])
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
        registerHierarchiesToWatch([directoryWithinFirst, secondDir])
        addSnapshotsInHierarchiesToWatch([secondDir, directoryWithinFirst])
        then:
        [directoryWithinFirst, secondDir].each { hierarchyToWatch ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        }
        0 * _

        when:
        registerHierarchiesToWatch([firstDir, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir]) })
        0 * _
    }

    def "does not watch snapshot roots in hierarchies to watch"() {
        def hierarchyToWatch = file("root").createDir()
        registerHierarchiesToWatch([hierarchyToWatch])
        def subDirInRootDir = hierarchyToWatch.file("some/path").createDir()
        def snapshotInRootDir = snapshotDirectory(subDirInRootDir)

        when:
        addSnapshot(snapshotInRootDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        0 * _

        when:
        buildFinished()
        then:
        0 * _

        when:
        invalidate(snapshotInRootDir)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
        0 * _

        when:
        addSnapshot(snapshotInRootDir)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, ([hierarchyToWatch])) })
        0 * _
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        registerHierarchiesToWatch([rootDir.parentFile])
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
        def hierarchyToWatch = rootDir.file("a/b/projectDir")
        def missingFile = hierarchyToWatch.file("c/missing.txt")

        when:
        registerHierarchiesToWatch([hierarchyToWatch])
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
        1 * watcher.startWatching({ equalIgnoringOrder(it, [hierarchyToWatch]) })
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

    private List<TestFile> addSnapshotsInHierarchiesToWatch(Collection<TestFile> projectRootDirectories) {
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
