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

package org.gradle.internal.vfs.impl

import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.vfs.watch.FileWatcherRegistry
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory
import spock.lang.Specification

class WatchingVirtualFileSystemTest extends Specification {
    def delegate = Mock(AbstractVirtualFileSystem)
    def watcherRegistryFactory = Mock(FileWatcherRegistryFactory)
    def watcherRegistry = Mock(FileWatcherRegistry)
    def watchingVirtualFileSystem = new WatchingVirtualFileSystem(watcherRegistryFactory, delegate, { -> true })
    def snapshotHierarchy = DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)

    def "invalidates the virtual file system before and after the build when watching is disabled"() {
        when:
        watchingVirtualFileSystem.afterStart(false)
        then:
        1 * delegate.invalidateAll()
        0 * _

        when:
        watchingVirtualFileSystem.beforeComplete(false, { -> new File("some/location") })
        then:
        1 * delegate.invalidateAll()
        0 * _
    }

    def "stops the watchers before the build when watching is disabled"() {
        when:
        watchingVirtualFileSystem.afterStart(true)
        then:
        _ * delegate.getRoot() >> snapshotHierarchy
        0 * _

        when:
        watchingVirtualFileSystem.beforeComplete(true, { -> new File("some/location") })
        then:
        _ * delegate.getRoot() >> snapshotHierarchy
        1 * watcherRegistryFactory.startWatching(snapshotHierarchy, _, _, _) >> watcherRegistry
        0 * _

        when:
        watchingVirtualFileSystem.afterStart(false)
        then:
        1 * delegate.invalidateAll()
        1 * watcherRegistry.close()
        0 * _
    }

    def "retains the virtual file system when watching is enabled"() {
        when:
        watchingVirtualFileSystem.afterStart(true)
        then:
        _ * delegate.getRoot() >> snapshotHierarchy
        0 * _

        when:
        watchingVirtualFileSystem.beforeComplete(true, { -> new File("some/location") })
        then:
        _ * delegate.getRoot() >> snapshotHierarchy
        1 * watcherRegistryFactory.startWatching(snapshotHierarchy, _, _, _) >> watcherRegistry
        0 * _

        when:
        watchingVirtualFileSystem.afterStart(true)
        then:
        _ * delegate.getRoot() >> snapshotHierarchy
        1 * watcherRegistry.getAndResetStatistics() >> Stub(FileWatcherRegistry.FileWatchingStatistics)
        0 * _
    }
}
