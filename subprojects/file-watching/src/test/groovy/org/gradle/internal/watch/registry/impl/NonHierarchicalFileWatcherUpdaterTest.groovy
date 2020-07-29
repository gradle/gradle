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

class NonHierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher) {
        new NonHierarchicalFileWatcherUpdater(watcher)
    }

    def "ignores project root directories"() {
        def projectRootDirectories = ["first", "second", "third"].collect { file(it).createDir() }
        def fileInProjectRootDirectory = file("first/inside/root/dir/file.txt")

        when:
        updater.updateRootProjectDirectories(projectRootDirectories)
        then:
        0 * _

        when:
        fileInProjectRootDirectory.createFile()
        addSnapshot(snapshotRegularFile(fileInProjectRootDirectory))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [fileInProjectRootDirectory.parentFile]) })
        0 * _

        when:
        updater.updateRootProjectDirectories([])
        then:
        0 * _
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        addSnapshot(rootDirSnapshot)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [rootDir.parentFile, rootDir]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[0])
        invalidate(rootDirSnapshot.children[1])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir.parentFile]) })
        0 * _

        when:
        invalidate(rootDirSnapshot.children[2])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [rootDir]) })
        0 * _
    }
}
