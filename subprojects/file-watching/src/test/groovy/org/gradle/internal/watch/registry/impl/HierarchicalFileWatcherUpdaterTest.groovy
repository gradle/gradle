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

import net.rubygrapefruit.platform.file.FileWatcher
import org.gradle.internal.watch.registry.FileWatcherUpdater

class HierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher) {
        new HierarchicalFileWatcherUpdater(watcher)
    }

    def "only adds watches for the roots of must watch directories"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        updater.updateMustWatchDirectories([firstDir, directoryWithinFirst, secondDir])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir, secondDir]) })
        0 * _
    }

    def "starts watching must watch directory which was beneath another must watch directory"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        updater.updateMustWatchDirectories([firstDir, directoryWithinFirst, secondDir])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir, secondDir]) })
        0 * _

        when:
        updater.updateMustWatchDirectories([directoryWithinFirst, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [firstDir]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        0 * _
    }

    def "stops watching must watch directory which is now beneath another must watch directory"() {
        def firstDir = file("first").createDir()
        def secondDir = file("second").createDir()
        def directoryWithinFirst = file("first/within").createDir()

        when:
        updater.updateMustWatchDirectories([directoryWithinFirst, secondDir])
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [directoryWithinFirst, secondDir]) })
        0 * _

        when:
        updater.updateMustWatchDirectories([firstDir, secondDir])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [directoryWithinFirst]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [firstDir]) })
        0 * _
    }

    def "does not watch snapshot roots in must watch directories"() {
        def rootDir = file("root").createDir()
        updater.updateMustWatchDirectories([rootDir])
        def subDirInRootDir = rootDir.file("some/path").createDir()
        def snapshotInRootDir = snapshotDirectory(subDirInRootDir)

        when:
        addSnapshot(snapshotInRootDir)
        then:
        0 * _

        when:
        updater.updateMustWatchDirectories([])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, ([subDirInRootDir.parentFile])) })
        0 * _
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _
    }
}
