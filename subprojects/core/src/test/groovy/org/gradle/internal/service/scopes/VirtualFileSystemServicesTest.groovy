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
import org.gradle.initialization.RootBuildLifecycleListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.vfs.AdditiveCacheLocations
import org.gradle.internal.vfs.RoutingVirtualFileSystem
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.internal.vfs.WatchingAwareVirtualFileSystem
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

    def "global virtual file system is informed about retention being #retentionEnabledString"() {
        _ * startParameter.getSystemPropertiesArgs() >> [:]
        _ * startParameter.getCurrentDir() >> new File("current/dir").absoluteFile
        _ * gradle.getStartParameter() >> startParameter
        def virtualFileSystem = Mock(WatchingAwareVirtualFileSystem)

        def buildLifecycleListener = new VirtualFileSystemBuildLifecycleListener(
            virtualFileSystem,
            { param -> retentionEnabled },
            { param -> false },
            { param -> null }
        )

        when:
        buildLifecycleListener.afterStart(gradle)
        then:
        1 * virtualFileSystem.afterStart(retentionEnabled)

        when:
        buildLifecycleListener.beforeComplete(gradle)
        then:
        1 * virtualFileSystem.beforeComplete(retentionEnabled, _)

        where:
        retentionEnabled << [true, false]
        retentionEnabledString = retentionEnabled ? "enabled" : "disabled"
    }

    Map<String, String> systemPropertyArgs(boolean retentionEnabled) {
        retentionEnabled
            ? [(VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY): "true"]
            : [:]
    }

}
