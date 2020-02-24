/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs

import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Function

class RoutingVirtualFileSystemTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    VirtualFileSystem gradleUserHomeVirtualFileSystem = Mock(VirtualFileSystem)
    VirtualFileSystem buildSessionScopedVirtualFileSystem = Mock(VirtualFileSystem)
    VirtualFileSystem routingVirtualFileSystem
    TestFile cacheDir
    boolean vfsRetained

    def setup() {
        cacheDir = tmpDir.createDir("cache")
        def fileStore = Stub(AdditiveCache)
        fileStore.additiveCacheRoots >> [cacheDir]
        routingVirtualFileSystem = new RoutingVirtualFileSystem(
            new DefaultAdditiveCacheLocations([fileStore]),
            gradleUserHomeVirtualFileSystem,
            buildSessionScopedVirtualFileSystem,
            { vfsRetained }
        )
    }

    def "routes method to the right underlying virtual file system"() {
        def userHomeFile = cacheDir.file("some/dir/a")
        def projectFile = tmpDir.file("build/some/file.txt")
        def snapshottingFilter = Mock(SnapshottingFilter)
        def action = {} as Runnable
        def hashFunction = { it } as Function<HashCode, HashCode>
        def location = inGradleUserHome ? userHomeFile.absolutePath : projectFile.absolutePath
        def fileSnapshot = Stub(RegularFileSnapshot) {
            getAbsolutePath() >> location
        }
        def expectedVirtualFileSystem = inGradleUserHome ? gradleUserHomeVirtualFileSystem : buildSessionScopedVirtualFileSystem
        def consumer = {} as Consumer<CompleteFileSystemLocationSnapshot>
        def snapshotFunction = { it } as Function<CompleteFileSystemLocationSnapshot, CompleteFileSystemLocationSnapshot>

        when:
        routingVirtualFileSystem.updateWithKnownSnapshot(fileSnapshot)
        then:
        1 * expectedVirtualFileSystem.updateWithKnownSnapshot(fileSnapshot)
        0 * _

        when:
        routingVirtualFileSystem.read(location, snapshotFunction)
        then:
        1 * expectedVirtualFileSystem.read(location, snapshotFunction)
        0 * _

        when:
        routingVirtualFileSystem.read(location, snapshottingFilter, consumer)
        then:
        1 * expectedVirtualFileSystem.read(location, snapshottingFilter, consumer)
        0 * _

        when:
        routingVirtualFileSystem.update([location], action)
        then:
        1 * expectedVirtualFileSystem.update([location], action)
        0 * _

        when:
        routingVirtualFileSystem.readRegularFileContentHash(location, hashFunction)
        then:
        1 * expectedVirtualFileSystem.readRegularFileContentHash(location, hashFunction)
        0 * _

        when:
        routingVirtualFileSystem.invalidateAll()
        then:
        1 * buildSessionScopedVirtualFileSystem.invalidateAll()
        0 * _

        where:
        inGradleUserHome << [true, false]
    }

    def "routes updates to the right underlying virtual file system"() {
        def userHomeFile = cacheDir.file("some/dir/a")
        def projectFile = tmpDir.file("build/some/file.txt")
        def updateAction = {} as Runnable

        when:
        routingVirtualFileSystem.update([userHomeFile.absolutePath], updateAction)
        then:
        1 * gradleUserHomeVirtualFileSystem.update([userHomeFile.absolutePath], updateAction)
        0 * _

        when:
        routingVirtualFileSystem.update([projectFile.absolutePath], updateAction)
        then:
        1 * buildSessionScopedVirtualFileSystem.update([projectFile.absolutePath], updateAction)
        0 * _

        when:
        routingVirtualFileSystem.update([userHomeFile.absolutePath, projectFile.absolutePath], updateAction)
        then:
        1 * gradleUserHomeVirtualFileSystem.update({ it as List == [userHomeFile.absolutePath] }, updateAction)
        1 * buildSessionScopedVirtualFileSystem.update({ it as List == [projectFile.absolutePath] }, updateAction)
        0 * _
    }

    def "routes to the Gradle user home virtual file system when retention is enabled"() {
        vfsRetained = true

        def userHomeFile = cacheDir.file("some/dir/a")
        def projectFile = tmpDir.file("build/some/file.txt")
        def snapshottingFilter = Mock(SnapshottingFilter)
        def action = {} as Runnable
        def hashFunction = { it } as Function<HashCode, HashCode>
        def location = inGradleUserHome ? userHomeFile.absolutePath : projectFile.absolutePath
        def fileSnapshot = Stub(RegularFileSnapshot) {
            getAbsolutePath() >> location
        }
        def consumer = {} as Consumer<CompleteFileSystemLocationSnapshot>
        def snapshotFunction = { it } as Function<CompleteFileSystemLocationSnapshot, CompleteFileSystemLocationSnapshot>


        when:
        routingVirtualFileSystem.updateWithKnownSnapshot(fileSnapshot)
        then:
        1 * gradleUserHomeVirtualFileSystem.updateWithKnownSnapshot(fileSnapshot)
        0 * _

        when:
        routingVirtualFileSystem.read(location, snapshotFunction)
        then:
        1 * gradleUserHomeVirtualFileSystem.read(location, snapshotFunction)
        0 * _

        when:
        routingVirtualFileSystem.read(location, snapshottingFilter, consumer)
        then:
        1 * gradleUserHomeVirtualFileSystem.read(location, snapshottingFilter, consumer)
        0 * _

        when:
        routingVirtualFileSystem.update([location], action)
        then:
        1 * gradleUserHomeVirtualFileSystem.update([location], action)
        0 * _

        when:
        routingVirtualFileSystem.readRegularFileContentHash(location, hashFunction)
        then:
        1 * gradleUserHomeVirtualFileSystem.readRegularFileContentHash(location, hashFunction)
        0 * _

        when:
        routingVirtualFileSystem.invalidateAll()
        then:
        1 * gradleUserHomeVirtualFileSystem.invalidateAll()
        0 * _

        where:
        inGradleUserHome << [true, false]
    }
}
