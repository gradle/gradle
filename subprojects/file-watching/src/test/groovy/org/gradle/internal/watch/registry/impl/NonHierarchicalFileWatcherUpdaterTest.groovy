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

    def "starts and stops watching must watch directories"() {
        def mustWatchDirectories = ["first", "second", "third"].collect { file(it).createDir() }

        when:
        updater.updateProjectRootDirectories(mustWatchDirectories)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, mustWatchDirectories) })
        0 * _

        when:
        updater.updateProjectRootDirectories([])
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, mustWatchDirectories) })
        0 * _
    }

    def "does not watch non-existing must watch directories"() {
        def existingMustWatchDirectories = ["first", "second"].collect { file(it).createDir() }
        def nonExistingMustWatchDirectory = file("non-existing")

        when:
        updater.updateProjectRootDirectories(existingMustWatchDirectories + nonExistingMustWatchDirectory)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, existingMustWatchDirectories) })
        0 * _
    }

    def "can change the must watch directories"() {
        def firstMustWatchDirectories = ["first", "second"].collect { file(it).createDir() }
        def secondMustWatchDirectories = ["second", "third"].collect { file(it).createDir() }

        when:
        updater.updateProjectRootDirectories(firstMustWatchDirectories)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, firstMustWatchDirectories) })
        0 * _

        when:
        updater.updateProjectRootDirectories(secondMustWatchDirectories)
        then:
        1 * watcher.stopWatching({ equalIgnoringOrder(it, [file("first")]) })
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [file("third")]) })
        0 * _
    }

    def "adds watches for all must watch directories"() {
        def mustWatchDirectoryRoots = ["first", "second"].collect { file(it).createDir()}
        def mustWatchDirectories = mustWatchDirectoryRoots + file("first/within").createDir()

        when:
        updater.updateProjectRootDirectories(mustWatchDirectories)
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, mustWatchDirectories) })
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
