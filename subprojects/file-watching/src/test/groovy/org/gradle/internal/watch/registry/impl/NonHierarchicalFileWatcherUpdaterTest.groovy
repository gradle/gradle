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

import java.util.function.Predicate

class NonHierarchicalFileWatcherUpdaterTest extends AbstractFileWatcherUpdaterTest {

    @Override
    FileWatcherUpdater createUpdater(FileWatcher watcher, Predicate<String> watchFilter) {
        new NonHierarchicalFileWatcherUpdater(watcher, watchFilter)
    }

    def "only watches directories in hierarchies to watch"() {
        def hierarchiesToWatch = ["first", "second", "third"].collect { file(it).createDir() }
        def fileInHierarchiesToWatch = file("first/inside/root/dir/file.txt")
        def fileOutsideOfHierarchiesToWatch = file("forth").file("someFile.txt")

        when:
        registerHierarchiesToWatch(hierarchiesToWatch)
        then:
        0 * _

        when:
        fileInHierarchiesToWatch.createFile()
        addSnapshot(snapshotRegularFile(fileInHierarchiesToWatch))
        then:
        1 * watcher.startWatching({ equalIgnoringOrder(it, [fileInHierarchiesToWatch.parentFile]) })
        0 * _

        when:
        fileOutsideOfHierarchiesToWatch.createFile()
        addSnapshot(snapshotRegularFile(fileOutsideOfHierarchiesToWatch))
        then:
        0 * _
        vfsHasSnapshotsAt(fileOutsideOfHierarchiesToWatch)

        when:
        buildFinished()
        then:
        _ * watchFilter.test(_) >> true
        0 * _
        !vfsHasSnapshotsAt(fileOutsideOfHierarchiesToWatch)
    }

    def "watchers are stopped when removing the last watched snapshot"() {
        def rootDir = file("root").createDir()
        ["first", "second", "third"].collect { rootDir.createFile(it) }
        def rootDirSnapshot = snapshotDirectory(rootDir)

        when:
        registerHierarchiesToWatch([rootDir])
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
