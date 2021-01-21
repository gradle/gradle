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

import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy
import org.gradle.internal.vfs.impl.VfsRootReference
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory
import org.gradle.internal.watch.registry.impl.DaemonDocumentationIndex
import org.gradle.internal.watch.vfs.VfsLogging
import org.gradle.internal.watch.vfs.WatchLogging
import org.gradle.internal.watch.vfs.WatchMode
import spock.lang.Specification

class WatchingVirtualFileSystemTest extends Specification {
    def watcherRegistryFactory = Mock(FileWatcherRegistryFactory)
    def watcherRegistry = Mock(FileWatcherRegistry)
    def emptySnapshotHierarchy = DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)
    def nonEmptySnapshotHierarchy = Stub(SnapshotHierarchy) {
        empty() >> emptySnapshotHierarchy
    }
    def rootReference = new VfsRootReference(nonEmptySnapshotHierarchy)
    def daemonDocumentationIndex = Mock(DaemonDocumentationIndex)
    def locationsUpdatedByCurrentBuild = Mock(LocationsWrittenByCurrentBuild)
    def buildOperationRunner = new TestBuildOperationExecutor()
    def watchingVirtualFileSystem = new WatchingVirtualFileSystem(
        watcherRegistryFactory,
        rootReference,
        daemonDocumentationIndex,
        locationsUpdatedByCurrentBuild
    )

    def "invalidates the virtual file system before and after the build when watching is disabled"() {
        when:
        rootReference.update { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DISABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        0 * _

        rootReference.getRoot() == emptySnapshotHierarchy

        when:
        rootReference.update { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.DISABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        0 * _

        rootReference.getRoot() == emptySnapshotHierarchy
    }

    def "stops the watchers before the build when watching is disabled"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.buildFinished(_, Integer.MAX_VALUE) >> rootReference.getRoot()
        0 * _

        when:
        rootReference.update { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.DISABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistry.close()
        0 * _

        rootReference.getRoot() == emptySnapshotHierarchy
    }

    def "retains the virtual file system when watching is enabled"() {
        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _

        when:
        watchingVirtualFileSystem.beforeBuildFinished(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner, Integer.MAX_VALUE)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.buildFinished(_, Integer.MAX_VALUE) >> rootReference.getRoot()
        0 * _

        when:
        rootReference.update { root -> nonEmptySnapshotHierarchy }
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.setDebugLoggingEnabled(false)
        0 * _

        rootReference.getRoot() == nonEmptySnapshotHierarchy
    }

    def "collects hierarchies to watch and notifies the vfs"() {
        def watchableHierarchy = new File("watchable")
        def anotherWatchableHierarchy = new File("anotherWatchable")
        def newWatchableHierarchy = new File("newWatchable")

        when:
        watchingVirtualFileSystem.registerWatchableHierarchy(watchableHierarchy)
        then:
        0 * _

        when:
        watchingVirtualFileSystem.afterBuildStarted(WatchMode.ENABLED, VfsLogging.NORMAL, WatchLogging.NORMAL, buildOperationRunner)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
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
        1 * watcherRegistry.buildFinished(_, Integer.MAX_VALUE) >> rootReference.getRoot()
        0 * _

        when:
        watchingVirtualFileSystem.registerWatchableHierarchy(newWatchableHierarchy)
        then:
        1 * watcherRegistry.registerWatchableHierarchy(newWatchableHierarchy, _)
    }
}
