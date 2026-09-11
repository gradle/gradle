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

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.TestVirtualFileSystem
import org.gradle.fileevents.FileWatcher
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.snapshot.impl.DirectorySnapshotter
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry
import org.gradle.internal.watch.registry.FileWatcherUpdater
import org.gradle.internal.watch.registry.WatchMode
import org.gradle.internal.watch.registry.WatcherVerificationResult
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Stream

@CleanupTestDirectory
abstract class AbstractFileWatcherUpdaterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def watcher = Mock(FileWatcher)
    def ignoredForWatching = [] as Set<String>
    Predicate<String> immutableLocationsFilter = ignoredForWatching::contains
    def probeLocationResolver = { hierarchy -> new File(hierarchy, ".gradle/file-watching.probe") } as Function<File, File>
    def probeRegistry = Stub(FileWatcherProbeRegistry)
    def watchableHierarchies = new WatchableHierarchies(probeRegistry, immutableLocationsFilter)
    def directorySnapshotter = new DirectorySnapshotter(TestFiles.fileHasher(), new StringInterner(), [], Stub(DirectorySnapshotterStatistics.Collector))
    FileWatcherUpdater updater
    def virtualFileSystem = new TestVirtualFileSystem(DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)) {
        @Override
        protected SnapshotHierarchy updateNotifyingListeners(AbstractVirtualFileSystem.UpdateFunction updateFunction) {
            def diffListener = new SnapshotCollectingDiffListener()
            def newRoot = updateFunction.update(diffListener)
            diffListener.publishSnapshotDiff { removed, added ->
                updater.virtualFileSystemContentsChanged(removed, added, newRoot)
            }
            return newRoot
        }
    }

    List<File> movedPaths = []
    AbstractFileWatcherUpdater.MovedDirectoryHandler movedWatchedDirectoriesSupplier = { SnapshotHierarchy vfsRoot -> movedPaths }

    def setup() {
        updater = createUpdater(watcher, watchableHierarchies)
    }

    /**
     * Returns 1 for non-hierarchical watchers, and 0 for hierarchical watchers.
     *
     * For use in interaction tests, when an interaction only happens for non-hierarchical watchers.
     * E.g.:
     * ifNonHierarchical * watcher.startWatching(_)
     */
    abstract int getIfNonHierarchical()

    /**
     * Returns 1 for hierarchical watchers, and 0 for non-hierarchical watchers.
     *
     * For use in interaction tests, when an interaction only happens for hierarchical watchers.
     * E.g.:
     * ifHierarchical * watcher.startWatching(_)
     */
    int getIfHierarchical() {
        return 1 - getIfNonHierarchical()
    }

    abstract FileWatcherUpdater createUpdater(FileWatcher watcher, WatchableHierarchies watchableHierarchies)

    def "does not watch directories outside of hierarchies to watch"() {
        def watchableHierarchies = ["first", "second", "third"].collect { file(it).createDir() }
        def fileOutsideOfWatchableHierarchies = file("forth").file("someFile.txt")

        when:
        registerWatchableHierarchies(watchableHierarchies)
        then:
        0 * _

        when:
        fileOutsideOfWatchableHierarchies.text = "hello"
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

    def "retains files in hierarchies ignored for watching"() {
        def watchableHierarchy = file("watchable").createDir()
        def fileOutsideOfWatchableHierarchy = file("outside").file("someFile.txt").createFile()
        def fileInDirectoryIgnoredForWatching = file("cache/some-cache/someFile.txt").createFile()
        ignoredForWatching.add(fileInDirectoryIgnoredForWatching.absolutePath)

        when:
        registerWatchableHierarchies([watchableHierarchy])
        then:
        0 * _

        when:
        addSnapshot(snapshotRegularFile(fileOutsideOfWatchableHierarchy))
        addSnapshot(snapshotRegularFile(fileInDirectoryIgnoredForWatching))
        then:
        0 * _
        vfsHasSnapshotsAt(fileOutsideOfWatchableHierarchy)
        vfsHasSnapshotsAt(fileInDirectoryIgnoredForWatching)

        when:
        buildFinished()
        then:
        0 * _
        !vfsHasSnapshotsAt(fileOutsideOfWatchableHierarchy)
        vfsHasSnapshotsAt(fileInDirectoryIgnoredForWatching)
    }

    def "fails when discovering a hierarchy to watch and there is already something in the VFS"() {
        def watchableHierarchy = file("watchable").createDir()
        def fileInWatchableHierarchy = watchableHierarchy.file("some/dir/file.txt").createFile()

        when:
        addSnapshot(snapshotRegularFile(fileInWatchableHierarchy))
        then:
        0 * _

        when:
        registerWatchableHierarchies([watchableHierarchy])
        then:
        def exception = thrown(IllegalStateException)
        exception.message == "Found existing snapshot at '${fileInWatchableHierarchy.absolutePath}' for unwatched hierarchy '${watchableHierarchy.absolutePath}'"
    }

    def "does not watch symlinks and removes symlinks at the end of the build"() {
        def watchableHierarchy = file("watchable").createDir()
        def symlinkInWatchableHierarchy = watchableHierarchy.file("some/dir/file.txt").createFile()

        when:
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotSymlinkedFile(symlinkInWatchableHierarchy))
        then:
        vfsHasSnapshotsAt(symlinkInWatchableHierarchy)
        0 * _

        when:
        buildFinished()
        then:
        !vfsHasSnapshotsAt(symlinkInWatchableHierarchy)
        0 * _
    }

    def "does not watch ignored files in a hierarchy to watch"() {
        def watchableHierarchy = file("watchable").createDir()
        def ignoredFileInHierarchy = watchableHierarchy.file("caches/cacheFile").createFile()
        ignoredForWatching.add(ignoredFileInHierarchy.absolutePath)
        ignoredForWatching.add(ignoredFileInHierarchy.parentFile.absolutePath)

        when:
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotRegularFile(ignoredFileInHierarchy))
        then:
        vfsHasSnapshotsAt(ignoredFileInHierarchy)
        0 * _

        when:
        buildFinished()
        then:
        vfsHasSnapshotsAt(ignoredFileInHierarchy)
        0 * _
    }

    def "fails when hierarchy to watch is ignored"() {
        def watchableHierarchy = file("watchable").createDir()
        ignoredForWatching.add(watchableHierarchy.absolutePath)

        when:
        registerWatchableHierarchies([watchableHierarchy])
        then:
        def exception = thrown(IllegalStateException)
        exception.message == "Unable to watch directory '${watchableHierarchy.absolutePath}' since it is within Gradle's caches"
    }

    def "stops watching hierarchies when maximum number of hierarchies to watch has been reached"() {
        int maxHierarchiesToWatch = 4
        def oldestRegisteredWatchableHierarchy = file("oldestWatchable").createDir()
        def watchableHierarchies = (1..maxHierarchiesToWatch - 1).collect { index -> file("watchable${index}").createDir() }
        def newestRegisteredWatchableHierarchy = file("newestWatchable").createDir()

        when:
        registerWatchableHierarchies([oldestRegisteredWatchableHierarchy] + watchableHierarchies)
        then:
        0 * _

        when:
        ([oldestRegisteredWatchableHierarchy] + watchableHierarchies).each {
            addSnapshot(snapshotRegularFile(it.file("watched.txt").createFile()))
        }
        then:
        ([oldestRegisteredWatchableHierarchy] + watchableHierarchies).each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        }
        when:
        registerWatchableHierarchies([newestRegisteredWatchableHierarchy])
        addSnapshot(snapshotRegularFile(newestRegisteredWatchableHierarchy.file("watched.txt").createFile()))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [newestRegisteredWatchableHierarchy]) })

        when:
        buildFinished(maxHierarchiesToWatch)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [oldestRegisteredWatchableHierarchy]) })

        !vfsHasSnapshotsAt(oldestRegisteredWatchableHierarchy)
        vfsHasSnapshotsAt(newestRegisteredWatchableHierarchy)
    }

    def "does not start watching unsupported file system for default watch mode"() {
        def unsupportedFileSystemMountPoint = file("unsupported").createDir()
        def unwatchableContent = unsupportedFileSystemMountPoint.file("file.txt").createFile()

        when:
        buildStarted(WatchMode.DEFAULT, [unsupportedFileSystemMountPoint])
        registerWatchableHierarchies([unsupportedFileSystemMountPoint])
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        0 * _

        when:
        buildFinished(Integer.MAX_VALUE, [unsupportedFileSystemMountPoint])
        then:
        !vfsHasSnapshotsAt(unwatchableContent)
        0 * _
    }

    def "starts watching unsupported file system when watching is enabled"() {
        def unsupportedFileSystemMountPoint = file("unsupported").createDir()
        def unwatchableContent = unsupportedFileSystemMountPoint.file("file.txt").createFile()

        when:
        buildStarted(WatchMode.ENABLED)
        registerWatchableHierarchies([unsupportedFileSystemMountPoint])
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.startWatching({ equalIgnoringOrder(it, [unsupportedFileSystemMountPoint]) })
        ifNonHierarchical * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(unsupportedFileSystemMountPoint)]) })
        0 * _

        when:
        buildFinished(Integer.MAX_VALUE)
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        0 * _
    }

    def "stops watching unsupported file system"() {
        def unsupportedFileSystemMountPoint = file("unsupported").createDir()
        def unwatchableContent = unsupportedFileSystemMountPoint.file("file.txt").createFile()

        when:
        buildStarted(WatchMode.ENABLED)
        registerWatchableHierarchies([unsupportedFileSystemMountPoint])
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.startWatching({ equalIgnoringOrder(it, [unsupportedFileSystemMountPoint]) })
        ifNonHierarchical * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(unsupportedFileSystemMountPoint)]) })
        0 * _

        when:
        buildFinished(Integer.MAX_VALUE)
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        0 * _

        when:
        registerWatchableHierarchies([unsupportedFileSystemMountPoint])
        buildStarted(WatchMode.DEFAULT, [unsupportedFileSystemMountPoint])
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [unsupportedFileSystemMountPoint]) })
        ifNonHierarchical * watcher.stopWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(unsupportedFileSystemMountPoint)]) })
        0 * _

        when:
        buildFinished(Integer.MAX_VALUE, [unsupportedFileSystemMountPoint])
        then:
        !vfsHasSnapshotsAt(unwatchableContent)
        0 * _
    }

    def "starts watching unsupported file system when watching becomes enabled"() {
        def unsupportedFileSystemMountPoint = file("unsupported").createDir()
        def unwatchableContent = unsupportedFileSystemMountPoint.file("file.txt").createFile()

        when:
        buildStarted(WatchMode.DEFAULT, [unsupportedFileSystemMountPoint])
        registerWatchableHierarchies([unsupportedFileSystemMountPoint])
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        0 * _

        when:
        buildFinished(Integer.MAX_VALUE, [unsupportedFileSystemMountPoint])
        then:
        !vfsHasSnapshotsAt(unwatchableContent)

        when:
        registerWatchableHierarchies([unsupportedFileSystemMountPoint])
        buildStarted(WatchMode.ENABLED)
        addSnapshot(snapshotRegularFile(unwatchableContent))
        then:
        vfsHasSnapshotsAt(unwatchableContent)
        1 * watcher.startWatching({ equalIgnoringOrder(it, [unsupportedFileSystemMountPoint]) })
        ifNonHierarchical * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(unsupportedFileSystemMountPoint)]) })
        0 * _
    }

    def "watching continues for watched hierarchies that are confirmed by watch probe"() {
        def watchableHierarchy = file("watchable").createDir()
        def watchableHierarchyProbeDir = watchableHierarchy.file(".gradle")
        def fileInWatchableHierarchy = watchableHierarchy.file("file.txt").createFile()

        def notWatchedHierarchy = file("not-watched").createDir()
        def notWatchedHierarchyProbeDir = notWatchedHierarchy.file(".gradle")
        def fileInNotWatchedHierarchy = notWatchedHierarchy.file("file.txt").createFile()

        def watchableHierarchies = [watchableHierarchy, notWatchedHierarchy]

        when:
        registerWatchableHierarchies(watchableHierarchies)
        addSnapshot(snapshotRegularFile(fileInWatchableHierarchy))
        then:
        vfsHasSnapshotsAt(watchableHierarchy)
        1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
        ifNonHierarchical * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchyProbeDir]) })
        _ * probeRegistry.getProbeDirectory(watchableHierarchy) >> watchableHierarchyProbeDir
        0 * _

        when:
        addSnapshot(snapshotRegularFile(fileInNotWatchedHierarchy))
        then:
        vfsHasSnapshotsAt(notWatchedHierarchy)

        1 * watcher.startWatching({ equalIgnoringOrder(it, [notWatchedHierarchy]) })
        ifNonHierarchical * watcher.startWatching({ equalIgnoringOrder(it, [notWatchedHierarchyProbeDir]) })
        _ * probeRegistry.getProbeDirectory(notWatchedHierarchy) >> notWatchedHierarchyProbeDir
        0 * _

        when:
        buildStarted()
        then:
        vfsHasSnapshotsAt(watchableHierarchy)
        !vfsHasSnapshotsAt(notWatchedHierarchy)

        1 * watcher.stopWatching({ equalIgnoringOrder(it, [notWatchedHierarchy]) })
        ifNonHierarchical * watcher.stopWatching({ equalIgnoringOrder(it, [notWatchedHierarchyProbeDir]) })
        _ * probeRegistry.getProbeDirectory(notWatchedHierarchy) >> notWatchedHierarchyProbeDir
        _ * probeRegistry.unprovenHierarchies() >> Stream.of(notWatchedHierarchy)
        0 * _
    }

    def "watchers are stopped when watched hierarchy is moved"() {
        def sourceDir = file("to-be-moved").createDir()
        def targetDir = file("target").createDir()
        def notMovedDir = file("normal").createDir()
        _ * probeRegistry.getProbeDirectory(_) >> { File hierarchy -> new File(hierarchy, ".gradle") }

        def watchableHierarchies = [sourceDir, notMovedDir]
        when:
        registerWatchableHierarchies(watchableHierarchies)
        watchableHierarchies.each {
            addSnapshotInWatchableHierarchy(it)
        }
        then:
        watchableHierarchies.each { watchableHierarchy ->
            1 * watcher.startWatching({ equalIgnoringOrder(it, [watchableHierarchy]) })
            ifNonHierarchical * watcher.startWatching({ equalIgnoringOrder(it, [directoryContainingSnapshot(watchableHierarchy)]) })
            ifNonHierarchical * watcher.startWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(watchableHierarchy)]) })
        }

        vfsHasSnapshotsAt(sourceDir)
        !vfsHasSnapshotsAt(targetDir)
        vfsHasSnapshotsAt(notMovedDir)
        0 * _

        updater.triggerWatchProbe(watchProbeFor(sourceDir).absolutePath)
        updater.triggerWatchProbe(watchProbeFor(notMovedDir).absolutePath)

        when:
        sourceDir.renameTo(targetDir)
        movedPaths << sourceDir
        buildStarted()
        then:
        !vfsHasSnapshotsAt(sourceDir)
        !vfsHasSnapshotsAt(targetDir)
        vfsHasSnapshotsAt(notMovedDir)
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [sourceDir]) })
        ifNonHierarchical * watcher.stopWatching({ equalIgnoringOrder(it, [directoryContainingSnapshot(sourceDir)]) })
        ifNonHierarchical * watcher.stopWatching({ equalIgnoringOrder(it, [probeRegistry.getProbeDirectory(sourceDir)]) })
        0 * _
    }

    def "a file added to a snapshotted directory makes it outdated"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        def sourceDirectory = watchableHierarchy.createDir("src")
        sourceDirectory.file("Foo.java").createFile()
        addSnapshot(snapshotDirectory(sourceDirectory))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }

        when: "a file appears that the watcher never reported"
        sourceDirectory.file("Bar.java").createFile()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then:
        verification.outdatedPaths == [sourceDirectory.absolutePath]

        when:
        virtualFileSystem.root = updater.updateVfsOnBuildStarted(virtualFileSystem.root, WatchMode.DEFAULT, [], verification)

        then:
        !vfsHasSnapshotsAt(sourceDirectory)
    }

    def "a file added under the probe directory is still detected"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        def probeDirectory = watchableHierarchy.createDir(".gradle")
        probeDirectory.file("file-watching.probe").createFile()
        addSnapshot(snapshotDirectory(probeDirectory))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(watchableHierarchy)

        when: "real state appears under .gradle without the watcher reporting it"
        probeDirectory.file("configuration-cache.bin").createFile()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "skipping the probe directory wholesale would have missed this"
        verification.outdatedPaths == [probeDirectory.absolutePath]
    }

    def "creating the probe directory does not make its parent outdated"() {
        given: "the hierarchy is snapshotted before the probe directory exists"
        def watchableHierarchy = file("hierarchy").createDir()
        watchableHierarchy.file("build.gradle").createFile()
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotDirectory(watchableHierarchy))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(watchableHierarchy)

        when: "arming creates it"
        watchableHierarchy.createDir(".gradle").file("file-watching-1.probe").createFile()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "the parent listing gained a name Gradle wrote for itself, which is not a change to report"
        verification.outdatedPaths.isEmpty()
    }

    def "removing the probe directory does not make its parent outdated"() {
        given: "the hierarchy is snapshotted while the probe directory exists"
        def watchableHierarchy = file("hierarchy").createDir()
        watchableHierarchy.file("build.gradle").createFile()
        def probeDirectory = watchableHierarchy.createDir(".gradle")
        probeDirectory.file("file-watching-1.probe").createFile()
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotDirectory(watchableHierarchy))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(watchableHierarchy)

        when: "it is gone from disk while the retained listing still names it"
        probeDirectory.deleteDir()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "the exclusion has to hold on the snapshot side as well as the disk side"
        !verification.outdatedPaths.contains(watchableHierarchy.absolutePath)
    }

    def "a file added under a probe directory the parent snapshot names is reported"() {
        given: "the parent is snapshotted while its probe directory already exists"
        def watchableHierarchy = file("hierarchy").createDir()
        watchableHierarchy.file("build.gradle").createFile()
        def probeDirectory = watchableHierarchy.createDir(".gradle")
        probeDirectory.file("file-watching-1.probe").createFile()
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotDirectory(watchableHierarchy))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(watchableHierarchy)

        when: "content the watcher never reported appears under it"
        probeDirectory.file("configuration-cache.bin").createFile()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "the directory is a child of the parent snapshot, so its own comparison catches it"
        verification.outdatedPaths == [probeDirectory.absolutePath]
    }

    def "a file replaced by a symlink to identical content is reported"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        def watched = watchableHierarchy.file("watched.txt")
        watched.text = "same bytes"
        addSnapshot(snapshotRegularFile(watched))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(watchableHierarchy)

        when: "the path becomes a link to a file the metadata cannot tell apart"
        def target = watchableHierarchy.file("target.txt")
        target.text = "same bytes"
        target.lastModified = watched.lastModified()
        watched.delete()
        java.nio.file.Files.createSymbolicLink(watched.toPath(), target.toPath())

        and:
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "following the link would vouch for content reached by a route Gradle does not watch"
        verification.outdatedPaths == [watched.absolutePath]
    }

    def "a nested hierarchy's probe directory is not reported by the outer walk"() {
        given: "the outer hierarchy is snapshotted before the inner hierarchy has a probe directory"
        def outer = file("outer").createDir()
        def inner = outer.createDir("inner")
        inner.file("build.gradle").createFile()
        registerWatchableHierarchies([outer, inner])
        addSnapshot(snapshotDirectory(inner))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(outer, inner)

        when: "arming the inner hierarchy creates its probe directory"
        inner.createDir(".gradle").file("file-watching-1.probe").createFile()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "an exclusion scoped to one hierarchy's probe directory would report the inner listing"
        verification.outdatedPaths.isEmpty()
    }

    def "retained state under a deleted probe directory is reported"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        def probeDirectory = watchableHierarchy.createDir(".gradle")
        probeDirectory.file("configuration-cache.bin").createFile()
        // The directory itself is retained, which is the shape an absence guard would skip whole.
        addSnapshot(snapshotDirectory(probeDirectory))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(watchableHierarchy)

        when: "the directory goes away with real state retained under it"
        probeDirectory.deleteDir()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "skipping the subtree here would vouch for state that is gone"
        verification.outdatedPaths == [probeDirectory.absolutePath]
    }

    def "a verified hierarchy survives the drop and an unverified one does not"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotRegularFile(watchableHierarchy.file("kept.txt").createFile()))
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probeRegistry.unprovenHierarchies() >> { Stream.of(watchableHierarchy) }

        when: "the probe never fires and no scan vouched for the hierarchy"
        buildStarted(WatchMode.DEFAULT, [], WatcherVerificationResult.EMPTY)

        then:
        !vfsHasSnapshotsAt(watchableHierarchy)
    }

    def "a hierarchy the scan verified is kept even though its probe never fired"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        addSnapshot(snapshotRegularFile(watchableHierarchy.file("kept.txt").createFile()))
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probeRegistry.unprovenHierarchies() >> { Stream.of(watchableHierarchy) }

        when:
        def verified = new WatcherVerificationResult([], [watchableHierarchy] as Set)
        buildStarted(WatchMode.DEFAULT, [], verified)

        then:
        vfsHasSnapshotsAt(watchableHierarchy)
    }

    def "re-arming the probe does not make its own directory outdated"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        def probeDirectory = watchableHierarchy.createDir(".gradle")
        probeDirectory.file("file-watching.probe").createFile()
        addSnapshot(snapshotDirectory(probeDirectory))
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        probePredicatesFrom(watchableHierarchy)

        when: "arming writes a file name the snapshot has never seen"
        probeDirectory.file("file-watching-1.probe").createFile()
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then:
        verification.outdatedPaths.isEmpty()
    }

    def "content in an immutable location is not scanned"() {
        given:
        def watchableHierarchy = file("hierarchy").createDir()
        registerWatchableHierarchies([watchableHierarchy])
        def cacheDirectory = watchableHierarchy.createDir("caches")
        def cachedFile = cacheDirectory.file("artifact.jar").createFile()
        addSnapshot(snapshotRegularFile(cachedFile))
        ignoredForWatching.add(cachedFile.absolutePath)
        probeRegistry.hasUnprovenHierarchies() >> true
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }

        when: "the immutable location changes behind the watcher's back"
        cachedFile.text = "rewritten"
        cachedFile.lastModified = cachedFile.lastModified() + 2000
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "Gradle manages that location itself, so the scan does not spend a stat on it"
        verification.outdatedPaths.isEmpty()
    }

    def "a hierarchy is not entered once the probe has answered"() {
        given: "two watchable hierarchies, each holding a file that changed behind the watcher's back"
        def hierarchies = [file("one").createDir(), file("two").createDir()]
        registerWatchableHierarchies(hierarchies)
        def changedFiles = hierarchies.collect { it.file("changed.txt").createFile() }
        changedFiles.each { it.text = "before" }
        changedFiles.each { addSnapshot(snapshotRegularFile(it)) }
        probeRegistry.getProbeDirectory(_) >> { File it -> new File(it, ".gradle") }
        // Unproven when the walk starts, answered before the next hierarchy is entered.
        probeRegistry.hasUnprovenHierarchies() >>> [true, false]

        when:
        changedFiles.each {
            it.text = "after"
            it.lastModified = it.lastModified() + 2000
        }
        def verification = updater.verifyWatcherIsCurrent(virtualFileSystem.root)

        then: "the walk stopped after one hierarchy, whichever the iteration reached first"
        verification.verifiedHierarchies.size() == 1
        verification.outdatedPaths.size() == 1

        and: "the hierarchy that was never entered is not vouched for"
        def skipped = hierarchies.find { !verification.verifiedHierarchies.contains(it) }
        verification.outdatedPaths.every { !it.startsWith(skipped.absolutePath) }
    }

    /**
     * Answers the probe predicates the way production does, by delegating to a real registry built
     * with this fixture's resolver. Re-stating the rule in a stub is how the earlier ones drifted
     * wider than the code they stood in for.
     */
    void probePredicatesFrom(File... hierarchies) {
        def real = new DefaultFileWatcherProbeRegistry(probeLocationResolver)
        hierarchies.each { real.registerProbe(it) }
        probeRegistry.isProbeFile(_) >> { String path -> real.isProbeFile(path) }
        probeRegistry.isProbeDirectory(_) >> { String path -> real.isProbeDirectory(path) }
    }

    TestFile file(Object... path) {
        temporaryFolder.testDirectory.file(path)
    }

    DirectorySnapshot snapshotDirectory(File directory) {
        directorySnapshotter.snapshot(directory.absolutePath, null, [:]) {} as DirectorySnapshot
    }

    void addSnapshot(FileSystemLocationSnapshot snapshot) {
        virtualFileSystem.store(snapshot.absolutePath, { snapshot } as Supplier<FileSystemLocationSnapshot>)
    }

    void invalidate(String absolutePath) {
        virtualFileSystem.invalidate([absolutePath])
    }

    void invalidate(FileSystemLocationSnapshot snapshot) {
        invalidate(snapshot.absolutePath)
    }

    static RegularFileSnapshot snapshotRegularFile(File regularFile) {
        def attributes = Files.readAttributes(regularFile.toPath(), BasicFileAttributes)
        new RegularFileSnapshot(
            regularFile.absolutePath,
            regularFile.name,
            TestFiles.fileHasher().hash(regularFile),
            DefaultFileMetadata.file(attributes.lastModifiedTime().toMillis(), attributes.size(), AccessType.DIRECT)
        )
    }

    static RegularFileSnapshot snapshotSymlinkedFile(File regularFile) {
        def attributes = Files.readAttributes(regularFile.toPath(), BasicFileAttributes)
        new RegularFileSnapshot(
            regularFile.absolutePath,
            regularFile.name,
            TestFiles.fileHasher().hash(regularFile),
            DefaultFileMetadata.file(attributes.lastModifiedTime().toMillis(), attributes.size(), AccessType.VIA_SYMLINK)
        )
    }

    static boolean equalIgnoringOrder(Object actual, Collection<?> expected) {
        List<?> actualSorted = (actual as List).toSorted()
        List<?> expectedSorted = (expected as List).toSorted()
        return actualSorted == expectedSorted
    }

    boolean vfsHasSnapshotsAt(File location) {
        return virtualFileSystem.root.rootSnapshotsUnder(location.absolutePath)
            .findAny()
            .present
    }

    void registerWatchableHierarchies(Iterable<File> watchableHierarchies) {
        watchableHierarchies.each { watchableHierarchy ->
            updater.registerWatchableHierarchy(watchableHierarchy, virtualFileSystem.root)
        }
    }

    File watchProbeFor(File watchableHierarchy) {
        probeLocationResolver.apply(watchableHierarchy)
    }

    SnapshotHierarchy buildStarted(WatchMode watchMode = WatchMode.DEFAULT, List<File> unsupportedFileSystems = [], WatcherVerificationResult verification = WatcherVerificationResult.EMPTY) {
        virtualFileSystem.root = updater.updateVfsOnBuildStarted(virtualFileSystem.root, watchMode, unsupportedFileSystems, verification)
        return virtualFileSystem.root
    }

    void buildFinished(int maximumNumberOfWatchedHierarchies = Integer.MAX_VALUE, List<File> unsupportedFileSystems = []) {
        virtualFileSystem.root = updater.updateVfsBeforeBuildFinished(virtualFileSystem.root, maximumNumberOfWatchedHierarchies, unsupportedFileSystems)
    }

    TestFile addSnapshotInWatchableHierarchy(TestFile projectRootDirectory) {
        def fileInside = directoryContainingSnapshot(projectRootDirectory).file("file.txt").createFile()
        addSnapshot(snapshotRegularFile(fileInside))
        return fileInside.parentFile
    }

    TestFile directoryContainingSnapshot(TestFile projectRootDirectory) {
        projectRootDirectory.file("some/subdir")
    }
}
