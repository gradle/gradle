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

import com.google.common.collect.ImmutableSet
import org.gradle.internal.snapshot.AtomicSnapshotHierarchyReference
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy
import org.gradle.internal.watch.registry.FileWatcherRegistry
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory
import org.gradle.internal.watch.registry.FileWatcherUpdater
import org.gradle.internal.watch.registry.impl.DaemonDocumentationIndex
import spock.lang.Specification

class DefaultFileSystemWatchingHandlerTest extends Specification {
    def watcherRegistryFactory = Mock(FileWatcherRegistryFactory)
    def watcherRegistry = Mock(FileWatcherRegistry)
    def fileWatcherUpdater = Mock(FileWatcherUpdater)
    def capturingUpdateFunctionDecorator = Mock(DelegatingDiffCapturingUpdateFunctionDecorator)
    def emptySnapshotHierarchy = DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)
    def nonEmptySnapshotHierarchy = Stub(SnapshotHierarchy) {
        empty() >> emptySnapshotHierarchy
    }
    def snapshotHierarchyReference = new AtomicSnapshotHierarchyReference(nonEmptySnapshotHierarchy)
    def daemonDocumentationIndex = Mock(DaemonDocumentationIndex)
    def recentlyCapturedSnapshots = Mock(RecentlyCapturedSnapshots)
    def watchingHandler = new DefaultFileSystemWatchingHandler(
        watcherRegistryFactory,
        snapshotHierarchyReference,
        capturingUpdateFunctionDecorator,
        { -> true },
        daemonDocumentationIndex,
        recentlyCapturedSnapshots
    )

    def "invalidates the virtual file system before and after the build when watching is disabled"() {
        when:
        snapshotHierarchyReference.update { nonEmptySnapshotHierarchy }
        watchingHandler.afterBuildStarted(false)
        then:
        0 * _

        snapshotHierarchyReference.get() == emptySnapshotHierarchy

        when:
        snapshotHierarchyReference.update { nonEmptySnapshotHierarchy }
        watchingHandler.beforeBuildFinished(false)
        then:
        0 * _

        snapshotHierarchyReference.get() == emptySnapshotHierarchy
    }

    def "stops the watchers before the build when watching is disabled"() {
        when:
        watchingHandler.afterBuildStarted(true)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.updateRootProjectDirectories(ImmutableSet.of())
        1 * capturingUpdateFunctionDecorator.setSnapshotDiffListener(_, _)
        0 * _

        when:
        watchingHandler.beforeBuildFinished(true)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.buildFinished()
        0 * _

        when:
        snapshotHierarchyReference.update { nonEmptySnapshotHierarchy }
        watchingHandler.afterBuildStarted(false)
        then:
        1 * watcherRegistry.close()
        1 * capturingUpdateFunctionDecorator.stopListening()
        0 * _

        snapshotHierarchyReference.get() == emptySnapshotHierarchy
    }

    def "retains the virtual file system when watching is enabled"() {
        when:
        watchingHandler.afterBuildStarted(true)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.updateRootProjectDirectories(ImmutableSet.of())
        1 * capturingUpdateFunctionDecorator.setSnapshotDiffListener(_, _)
        0 * _

        when:
        watchingHandler.beforeBuildFinished(true)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.buildFinished()
        0 * _

        when:
        snapshotHierarchyReference.update { nonEmptySnapshotHierarchy }
        watchingHandler.afterBuildStarted(true)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        0 * _

        snapshotHierarchyReference.get() == nonEmptySnapshotHierarchy
    }

    def "collects build root directories and notifies the vfs"() {
        def rootDirectory = new File("root")
        def anotherBuildRootDirectory = new File("anotherRoot")
        def newRootDirectory = new File("newRoot")

        when:
        watchingHandler.buildRootDirectoryAdded(rootDirectory)
        then:
        0 * _

        when:
        watchingHandler.afterBuildStarted(true)
        then:
        1 * watcherRegistryFactory.createFileWatcherRegistry(_) >> watcherRegistry
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.updateRootProjectDirectories(ImmutableSet.of(rootDirectory))
        1 * capturingUpdateFunctionDecorator.setSnapshotDiffListener(_, _)
        0 * _

        when:
        watchingHandler.buildRootDirectoryAdded(anotherBuildRootDirectory)
        then:
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.updateRootProjectDirectories(ImmutableSet.of(rootDirectory, anotherBuildRootDirectory))

        when:
        watchingHandler.beforeBuildFinished(true)
        then:
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.buildFinished()
        0 * _

        when:
        watchingHandler.buildRootDirectoryAdded(newRootDirectory)
        then:
        1 * watcherRegistry.fileWatcherUpdater >> fileWatcherUpdater
        1 * fileWatcherUpdater.updateRootProjectDirectories(ImmutableSet.of(newRootDirectory))
    }
}
