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

package org.gradle.internal.watch.vfs.impl

import net.rubygrapefruit.platform.NativeException
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory
import org.gradle.internal.watch.registry.WatchMode
import org.gradle.internal.watch.registry.WatcherVerificationResult
import org.gradle.internal.watch.registry.impl.FileSystemWatchingDocumentationIndex
import org.gradle.internal.watch.vfs.FileChangeListeners
import org.gradle.internal.watch.vfs.VfsLogging
import org.gradle.internal.watch.vfs.WatchableFileSystemDetector
import spock.lang.Specification

import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WatchingVirtualFileSystemTest extends Specification {
    def watcherRegistryFactory = Mock(FileWatcherRegistryFactory)
    def watcherRegistry = Mock(FileWatcherRegistry)
    def emptySnapshotHierarchy = DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)
    def nonEmptySnapshotHierarchy = Stub(SnapshotHierarchy) {
        empty() >> emptySnapshotHierarchy
    }
    def documentationIndex = Mock(FileSystemWatchingDocumentationIndex)
    def locationsUpdatedByCurrentBuild = Mock(FileWatchingFilter)
    def buildOperationRunner = new TestBuildOperationRunner()
    def watchableFileSystemDetector = Mock(WatchableFileSystemDetector)
    def fileChangeListeners = Mock(FileChangeListeners)
    def watchingVirtualFileSystem = new WatchingVirtualFileSystem(
        watcherRegistryFactory,
        nonEmptySnapshotHierarchy,
        documentationIndex,
        locationsUpdatedByCurrentBuild,
        watchableFileSystemDetector,
        fileChangeListeners
    )

    def "invalidates the virtual file system before and after the build when watching is disabled"() {
        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DISABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        watchingVirtualFileSystem.root == emptySnapshotHierarchy

        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.DISABLED, VfsLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        watchingVirtualFileSystem.afterBuildFinished()
        then:
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        watchingVirtualFileSystem.root == emptySnapshotHierarchy
    }

    def "stops the watchers before the build when watching is disabled"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _, _) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsBeforeBuildFinished(_, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildFinished()
        then:
        1 * watcherRegistry.updateVfsAfterBuildFinished(_) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DISABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistry.close()
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        watchingVirtualFileSystem.root == emptySnapshotHierarchy
    }

    def "a probe file event is not broadcast to file change listeners"() {
        given:
        FileWatcherRegistry.ChangeHandler handler = null

        when: "watching starts, so the handler chain exists"
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> { FileWatcherRegistry.ChangeHandler it ->
            handler = it
            watcherRegistry
        }
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _, _) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when: "an event arrives for Gradle's own probe file"
        handler.handleChange(FileWatcherRegistry.Type.MODIFIED, Paths.get("/project/.gradle/file-system-1.probe"))
        then: "arming the probe every build must not make a continuous build retrigger itself"
        _ * watcherRegistry.isProbeFile("/project/.gradle/file-system-1.probe") >> true
        _ * locationsUpdatedByCurrentBuild.shouldWatchLocation(_) >> true
        0 * fileChangeListeners.broadcastChange(_, _)

        when: "an ordinary event arrives"
        handler.handleChange(FileWatcherRegistry.Type.MODIFIED, Paths.get("/project/src/Foo.java"))
        then:
        _ * watcherRegistry.isProbeFile("/project/src/Foo.java") >> false
        _ * locationsUpdatedByCurrentBuild.shouldWatchLocation(_) >> true
        1 * fileChangeListeners.broadcastChange(_, _)
    }

    def "the watcher is verified before the virtual file system is locked"() {
        given:
        def lockTaken = new CountDownLatch(1)

        when: "watching starts"
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _, _) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when: "the next build verifies the watcher"
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)

        then: "another thread can still take the update lock while the verification runs"
        1 * watcherRegistry.verifyWatcherIsCurrent(_) >> {
            // The watcher consumer thread needs this same lock to deliver a probe event. Holding it
            // here is what starved the probe, so the verification must run outside it.
            Thread.start {
                watchingVirtualFileSystem.updateRootUnderLock { root -> root }
                lockTaken.countDown()
            }
            assert lockTaken.await(10, TimeUnit.SECONDS)
            return WatcherVerificationResult.EMPTY
        }
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _, _) >> watchingVirtualFileSystem.root
        0 * _
    }

    def "retains the virtual file system when watching is enabled"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _, _) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsBeforeBuildFinished(_, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildFinished()
        then:
        1 * watcherRegistry.updateVfsAfterBuildFinished(_) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistry.updateVfsOnBuildStarted(_ as SnapshotHierarchy, WatchMode.ENABLED, [], _) >> { SnapshotHierarchy root, watchMode, unsupportedFileSystems, verification -> root }
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        watchingVirtualFileSystem.root == nonEmptySnapshotHierarchy
    }

    def "collects hierarchies to watch and notifies the vfs"() {
        def watchableHierarchy = new File("watchable")
        def watcherProbe = new File(watchableHierarchy, ".gradle/watch-probe")
        def anotherWatchableHierarchy = new File("anotherWatchable")
        def anotherWatcherProbe = new File(anotherWatchableHierarchy, ".gradle/watch-probe")
        def newWatchableHierarchy = new File("newWatchable")
        def newWatcherProbe = new File(newWatchableHierarchy, ".gradle/watch-probe")

        when:
        watchingVirtualFileSystem.registerWatchableHierarchy(watchableHierarchy)
        then:
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _, _) >> watchingVirtualFileSystem.root
        1 * watcherRegistry.registerWatchableHierarchy(watchableHierarchy, _)
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.registerWatchableHierarchy(anotherWatchableHierarchy)
        then:
        1 * watcherRegistry.registerWatchableHierarchy(anotherWatchableHierarchy, _)

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsBeforeBuildFinished(_, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildFinished()

        then:
        1 * watcherRegistry.updateVfsAfterBuildFinished(_) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.registerWatchableHierarchy(newWatchableHierarchy)
        then:
        1 * watcherRegistry.registerWatchableHierarchy(newWatchableHierarchy, _)
    }

    def "detects unsupported file systems on default watch mode"() {
        def unsupportedFileSystems = [new File("unsupported")]

        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> unsupportedFileSystems.stream()
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, unsupportedFileSystems, _) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.DEFAULT, VfsLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsBeforeBuildFinished(_, Integer.MAX_VALUE, unsupportedFileSystems) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildFinished()
        then:
        1 * watcherRegistry.updateVfsAfterBuildFinished(_) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        unsupportedFileSystems = [new File("unsupported"), new File("anotherUnsupported")]
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> unsupportedFileSystems.stream()
        1 * watcherRegistry.updateVfsOnBuildStarted(_ as SnapshotHierarchy, WatchMode.DEFAULT, unsupportedFileSystems, _) >> { SnapshotHierarchy root, watchMode, it, verification -> root }
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _
    }

    def "does not start watching when unable to detect unsupported file systems"() {
        when:
        def result = watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, buildOperationRunner)
        then:
        !result
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> { throw new NativeException("Failed") }
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _
    }

    def "stops file system watching when unable to detect unsupported file systems"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, [], _) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsBeforeBuildFinished(_, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildFinished()
        then:
        1 * watcherRegistry.updateVfsAfterBuildFinished(_) >> watchingVirtualFileSystem.root
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _

        when:
        def result = watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, buildOperationRunner)
        then:
        !result
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> { throw new NativeException("Failed") }
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.close()
        _ * watcherRegistry.verifyWatcherIsCurrent(_) >> WatcherVerificationResult.EMPTY
        0 * _
    }
}
