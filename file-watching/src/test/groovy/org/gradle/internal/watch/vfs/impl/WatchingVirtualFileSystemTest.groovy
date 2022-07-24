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
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory
import org.gradle.internal.watch.registry.WatchMode
import org.gradle.internal.watch.registry.impl.DaemonDocumentationIndex
import org.gradle.internal.watch.vfs.FileChangeListeners
import org.gradle.internal.watch.vfs.VfsLogging
import org.gradle.internal.watch.vfs.WatchLogging
import org.gradle.internal.watch.vfs.WatchableFileSystemDetector
import spock.lang.Specification

class WatchingVirtualFileSystemTest extends Specification {
    def watcherRegistryFactory = Mock(FileWatcherRegistryFactory)
    def watcherRegistry = Mock(FileWatcherRegistry)
    def emptySnapshotHierarchy = DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)
    def nonEmptySnapshotHierarchy = Stub(SnapshotHierarchy) {
        empty() >> emptySnapshotHierarchy
    }
    def daemonDocumentationIndex = Mock(DaemonDocumentationIndex)
    def locationsUpdatedByCurrentBuild = Mock(LocationsWrittenByCurrentBuild)
    def buildOperationRunner = new TestBuildOperationExecutor()
    def watchableFileSystemDetector = Mock(WatchableFileSystemDetector)
    def fileChangeListeners = Mock(FileChangeListeners)
    def watchingVirtualFileSystem = new WatchingVirtualFileSystem(
        watcherRegistryFactory,
        nonEmptySnapshotHierarchy,
        daemonDocumentationIndex,
        locationsUpdatedByCurrentBuild,
        watchableFileSystemDetector,
        fileChangeListeners
    )

    def "invalidates the virtual file system before and after the build when watching is disabled"() {
        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DISABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        0 * _

        watchingVirtualFileSystem.root == emptySnapshotHierarchy

        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.DISABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        0 * _

        watchingVirtualFileSystem.root == emptySnapshotHierarchy
    }

    def "stops the watchers before the build when watching is disabled"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _) >> watchingVirtualFileSystem.root
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsOnBuildFinished(_, WatchMode.ENABLED, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        0 * _

        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DISABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistry.close()
        0 * _

        watchingVirtualFileSystem.root == emptySnapshotHierarchy
    }

    def "retains the virtual file system when watching is enabled"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _) >> watchingVirtualFileSystem.root
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsOnBuildFinished(_, WatchMode.ENABLED, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        0 * _

        when:
        watchingVirtualFileSystem.updateRootUnderLock { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistry.updateVfsOnBuildStarted(_ as SnapshotHierarchy, WatchMode.ENABLED, []) >> { SnapshotHierarchy root, watchMode, unsupportedFileSystems -> root }
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.setDebugLoggingEnabled(false)
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
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, _) >> watchingVirtualFileSystem.root
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        1 * watcherRegistry.registerWatchableHierarchy(watchableHierarchy, _)
        0 * _

        when:
        watchingVirtualFileSystem.registerWatchableHierarchy(anotherWatchableHierarchy)
        then:
        1 * watcherRegistry.registerWatchableHierarchy(anotherWatchableHierarchy, _)

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsOnBuildFinished(_, WatchMode.ENABLED, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        0 * _

        when:
        watchingVirtualFileSystem.registerWatchableHierarchy(newWatchableHierarchy)
        then:
        1 * watcherRegistry.registerWatchableHierarchy(newWatchableHierarchy, _)
    }

    def "detects unsupported file systems on default watch mode"() {
        def unsupportedFileSystems = [new File("unsupported")]

        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> unsupportedFileSystems.stream()
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, unsupportedFileSystems) >> watchingVirtualFileSystem.root
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.DEFAULT, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsOnBuildFinished(_, WatchMode.DEFAULT, Integer.MAX_VALUE, unsupportedFileSystems) >> watchingVirtualFileSystem.root
        0 * _

        when:
        unsupportedFileSystems = [new File("unsupported"), new File("anotherUnsupported")]
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> unsupportedFileSystems.stream()
        1 * watcherRegistry.updateVfsOnBuildStarted(_ as SnapshotHierarchy, WatchMode.DEFAULT, unsupportedFileSystems) >> { SnapshotHierarchy root, watchMode, it -> root }
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _
    }

    def "does not start watching when unable to detect unsupported file systems"() {
        when:
        def result = watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        !result
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> { throw new NativeException("Failed") }
        0 * _
    }

    def "stops file system watching when unable to detect unsupported file systems"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.updateVfsOnBuildStarted(_, _, []) >> watchingVirtualFileSystem.root
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.updateVfsOnBuildFinished(_, WatchMode.ENABLED, Integer.MAX_VALUE, []) >> watchingVirtualFileSystem.root
        0 * _

        when:
        def result = watchingVirtualFileSystem.afterBuildStarted(WatchMode.DEFAULT, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        !result
        1 * watchableFileSystemDetector.detectUnsupportedFileSystems() >> { throw new NativeException("Failed") }
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.close()
        0 * _
    }
}
