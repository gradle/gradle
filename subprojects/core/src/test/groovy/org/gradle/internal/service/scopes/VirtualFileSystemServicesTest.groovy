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

package org.gradle.internal.service.scopes

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.vfs.AdditiveCacheLocations
import org.gradle.internal.vfs.RoutingVirtualFileSystem
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.internal.vfs.watch.FileWatcherRegistry
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class VirtualFileSystemServicesTest extends Specification {
    def additiveCacheLocations = Mock(AdditiveCacheLocations)
    def fileHasher = Mock(FileHasher)
    def fileSystem = Mock(FileSystem)
    def listenerManager = Mock(ListenerManager)
    def startParameter = Mock(StartParameter)
    def stringInterner = Mock(StringInterner)
    def gradle = Mock(GradleInternal)
    def watcherRegistryFactory = Mock(FileWatcherRegistryFactory)
    def watcherRegistry = Mock(FileWatcherRegistry)

    def "global virtual file system is not invalidated from the build session scope listener after the build completed (retention enabled: #retentionEnabled)"() {
        def gradleUserHomeVirtualFileSystem = Mock(VirtualFileSystem)
        RootBuildLifecycleListener rootBuildLifecycleListener
        _ * startParameter.getSystemPropertiesArgs() >> systemPropertyArgs(retentionEnabled)

        when:
        def buildSessionScopedVirtualFileSystem = new VirtualFileSystemServices.BuildSessionServices().createVirtualFileSystem(
            additiveCacheLocations,
            fileHasher,
            fileSystem,
            listenerManager,
            startParameter,
            fileSystem,
            stringInterner,
            gradleUserHomeVirtualFileSystem
        )
        then:
        buildSessionScopedVirtualFileSystem instanceof RoutingVirtualFileSystem

        1 * listenerManager.addListener(_ as RootBuildLifecycleListener) >> { RootBuildLifecycleListener listener ->
            rootBuildLifecycleListener = listener
        }

        when:
        rootBuildLifecycleListener.beforeComplete(gradle)
        then:
        0 * _

        where:
        retentionEnabled << [true, false]
    }

    def "global virtual file system is not invalidated after the build completed when retention is enabled"() {
        RootBuildLifecycleListener rootBuildLifecycleListener
        _ * startParameter.getSystemPropertiesArgs() >> systemPropertyArgs(true)
        _ * gradle.getStartParameter() >> startParameter
        def rootProject = Mock(ProjectInternal)
        _ * gradle.getRootProject() >> rootProject
        _ * rootProject.getProjectDir() >> new File("some/project/dir")
        def path = "/some/path"
        def snapshot = new RegularFileSnapshot(path, "path", HashCode.fromInt(1234), new FileMetadata(0, 0))

        when:
        def virtualFileSystem = new VirtualFileSystemServices.GradleUserHomeServices().createVirtualFileSystem(
            additiveCacheLocations,
            fileHasher,
            fileSystem,
            watcherRegistryFactory,
            listenerManager,
            fileSystem,
            stringInterner
        )
        then:
        1 * listenerManager.addListener(_ as RootBuildLifecycleListener) >> { RootBuildLifecycleListener listener ->
            rootBuildLifecycleListener = listener
        }

        when:
        virtualFileSystem.updateWithKnownSnapshot(snapshot)
        rootBuildLifecycleListener.beforeComplete(gradle)
        then:
        virtualFileSystem.read(path, { it }) == snapshot
        1 * watcherRegistryFactory.startWatching(_, _, Collections.singleton(rootProject.projectDir.absolutePath)) >> watcherRegistry
    }

    Map<String, String> systemPropertyArgs(boolean retentionEnabled) {
        retentionEnabled
            ? [(VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY): "true"]
            : [:]
    }

}
