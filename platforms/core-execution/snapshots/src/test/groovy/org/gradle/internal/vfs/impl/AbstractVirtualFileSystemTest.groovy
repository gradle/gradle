/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.snapshot.TestSnapshotFixture
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.function.Supplier

class AbstractVirtualFileSystemTest extends ConcurrentSpec implements TestSnapshotFixture {

    def vfs = new AbstractVirtualFileSystem(DefaultSnapshotHierarchy.empty(CaseSensitivity.CASE_SENSITIVE)) {
        @Override
        protected SnapshotHierarchy updateNotifyingListeners(AbstractVirtualFileSystem.UpdateFunction updateFunction) {
            return updateFunction.update(SnapshotHierarchy.NodeDiffListener.NOOP)
        }
    }

    def "does not store snapshot when invalidation happened in between"() {
        def location = '/my/location/new'
        when:
        start {
            vfs.store(location, { ->
                instant.snapshottingStarted
                thread.blockUntil.invalidated
                instant.snapshottingFinished
                return directory(location, [])
            } as Supplier<FileSystemLocationSnapshot>)
        }
        async {
            thread.blockUntil.snapshottingStarted
            vfs.invalidate(['/my/location/new/something'])
            instant.invalidated
        }
        then:
        instant.snapshottingStarted < instant.invalidated
        instant.invalidated < instant.snapshottingFinished
        !vfs.findSnapshot(location).present
    }

     def "only stores non-invalidated children when using store action"() {
        def location = '/my/location/new'
        when:
        start {
            vfs.store(location, { vfsStore ->
                instant.snapshottingStarted
                vfsStore.store(regularFile("${location}/some/child"))
                vfsStore.store(regularFile("${location}/other/child"))
                instant.partialSnapshotsStored
                thread.blockUntil.invalidated
                vfsStore.store(regularFile("${location}/other/child2"))
                vfsStore.store(regularFile("${location}/some/child2"))
                instant.snapshottingFinished
                return directory(location, [])
            } as VirtualFileSystem.StoringAction)
        }
        async {
            thread.blockUntil.partialSnapshotsStored
            vfs.invalidate(["${location}/some".toString()])
            instant.invalidated
        }
        then:
        instant.snapshottingStarted < instant.partialSnapshotsStored
        instant.partialSnapshotsStored < instant.invalidated
        instant.invalidated < instant.snapshottingFinished
        !vfs.findSnapshot(location).present
        !vfs.findSnapshot("${location}/some/child").present
        !vfs.findSnapshot("${location}/some/child2").present
        vfs.findSnapshot("${location}/other/child").present
        vfs.findSnapshot("${location}/other/child2").present
    }

    def "does not store snapshot when invalidate all happened in between"() {
        def location = '/my/location/new'
        when:
        start {
            vfs.store(location, { ->
                instant.snapshottingStarted
                thread.blockUntil.invalidated
                instant.snapshottingFinished
                return directory(location, [])
            } as Supplier<FileSystemLocationSnapshot>)
        }
        async {
            thread.blockUntil.snapshottingStarted
            vfs.invalidateAll()
            instant.invalidated
        }
        then:
        instant.snapshottingStarted < instant.invalidated
        instant.invalidated < instant.snapshottingFinished
        !vfs.findSnapshot(location).present
    }

    def "stores snapshot when invalidation happens before"() {
        def location = '/my/location/new'

        when:
        vfs.invalidate(['/my/location/new/something'])
        vfs.store(location, { ->
            return directory(location, [])
        } as Supplier<FileSystemLocationSnapshot>)
        then:
        vfs.findSnapshot(location).present
    }
}
