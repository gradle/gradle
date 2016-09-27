/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.hash.Hasher
import org.gradle.cache.PersistentIndexedCache
import org.gradle.internal.resource.TextResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CachingFileSnapshotterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def target = Mock(Hasher)
    def cache = Mock(PersistentIndexedCache)
    def cacheAccess = Mock(TaskHistoryStore)
    def hash = Hashing.md5().hashString("hello", Charsets.UTF_8)
    def file = tmpDir.createFile("testfile")
    CachingFileSnapshotter hasher

    def setup() {
        file.write("some-content")
        1 * cacheAccess.createCache("fileHashes", _, _) >> cache
        hasher = new CachingFileSnapshotter(target, cacheAccess, new StringInterner());
    }

    def hashesFileWhenHashNotCached() {
        when:
        def result = hasher.snapshot(file)

        then:
        result.hash == hash

        and:
        1 * cache.get(file.getAbsolutePath()) >> null
        1 * target.hash(file) >> hash
        1 * cache.put(file.getAbsolutePath(), _) >> { String key, CachingFileSnapshotter.FileInfo fileInfo ->
            fileInfo.hash == hash
            fileInfo.length == file.length()
            fileInfo.timestamp == file.lastModified()
        }
        0 * _._
    }

    def hashesFileWhenLengthHasChanged() {
        when:
        def result = hasher.snapshot(file)

        then:
        result.hash == hash

        and:
        1 * cache.get(file.getAbsolutePath()) >> new CachingFileSnapshotter.FileInfo(hash, 1024, file.lastModified())
        1 * target.hash(file) >> hash
        1 * cache.put(file.getAbsolutePath(), _) >> { String key, CachingFileSnapshotter.FileInfo fileInfo ->
            fileInfo.hash == hash
            fileInfo.length == file.length()
            fileInfo.timestamp == file.lastModified()
        }
        0 * _._
    }

    def hashesFileWhenTimestampHasChanged() {
        when:
        def result = hasher.snapshot(file)

        then:
        result.hash == hash

        and:
        1 * cache.get(file.getAbsolutePath()) >> new CachingFileSnapshotter.FileInfo(hash, file.length(), 124)
        1 * target.hash(file) >> hash
        1 * cache.put(file.getAbsolutePath(), _) >> { String key, CachingFileSnapshotter.FileInfo fileInfo ->
            fileInfo.hash == hash
            fileInfo.length == file.length()
            fileInfo.timestamp == file.lastModified()
        }
        0 * _._
    }

    def doesNotHashFileWhenTimestampAndLengthHaveNotChanged() {
        when:
        def result = hasher.snapshot(file)

        then:
        result.hash == hash

        and:
        1 * cache.get(file.getAbsolutePath()) >> new CachingFileSnapshotter.FileInfo(hash, file.length(), file.lastModified())
        0 * _._
    }

    def hashesBackingFileWhenResourceIsBackedByFile() {
        def resource = Mock(TextResource)

        when:
        def result = hasher.snapshot(resource)

        then:
        result.hash == hash

        and:
        1 * resource.file >> file
        1 * cache.get(file.getAbsolutePath()) >> new CachingFileSnapshotter.FileInfo(hash, file.length(), file.lastModified())
        0 * _._
    }

    def hashesContentWhenResourceIsNotBackedByFile() {
        def resource = Mock(TextResource)

        when:
        def result = hasher.snapshot(resource)

        then:
        result.hash == hash

        and:
        1 * resource.file >> null
        1 * resource.text >> "hello"
        0 * _._
    }
}
