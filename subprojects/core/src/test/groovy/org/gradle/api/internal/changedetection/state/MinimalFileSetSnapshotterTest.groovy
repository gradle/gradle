/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.cache.CacheAccess
import org.gradle.cache.PersistentStore
import org.gradle.cache.internal.MapBackedInMemoryStore
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static TaskFilePropertyCompareType.UNORDERED

class MinimalFileSetSnapshotterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def stringInterner = new StringInterner()
    PersistentStore persistentStore = new MapBackedInMemoryStore()
    def snapshotter = new CachingFileSnapshotter(new DefaultHasher(), persistentStore, stringInterner)

    CacheAccess cacheAccess = persistentStore
    FileResolver fileResolver = Mock()
    FileSystem fileSystem = Mock()

    def minimalFileSnapshotter = new MinimalFileSetSnapshotter(snapshotter, cacheAccess, stringInterner, fileResolver, fileSystem)

    def snapshot

    def "snapshots files from input"() {
        given:
        def included = tmpDir.file("included") << "included"
        def notIncluded = tmpDir.file("notIncluded") << "not included"
        def includedDirectory = tmpDir.createDir("includedDirectory")
        def fileInDirectory = includedDirectory.file("file") << "contents"
        def missing = tmpDir.file("missing")

        cacheAccess.useCache(_, (Runnable)_) >> {
            it[1].run()
        }

        def collection = new SimpleFileCollection(included, missing, includedDirectory)

        when:
        snapshot = minimalFileSnapshotter.snapshot(collection, UNORDERED)

        then:
        findSnapshot(included) instanceof FileHashSnapshot
        findSnapshot(missing) instanceof MissingFileSnapshot
        findSnapshot(includedDirectory) instanceof DirSnapshot
        !findSnapshot(fileInDirectory)
        !findSnapshot(notIncluded)

        and:
        // getFiles() returns missing file snapshots and existing file snapshots, but not directories
        snapshot.files.sort() == [ included, missing ]
    }

    IncrementalFileSnapshot findSnapshot(File file) {
        snapshot.snapshots.get(file.absolutePath)
    }
}
