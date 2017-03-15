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
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.CachingFileHasher.FileInfo
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.hash.FileHasher
import org.gradle.cache.PersistentIndexedCache
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata
import org.gradle.internal.resource.TextResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CachingFileHasherTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def target = Mock(FileHasher)
    def cache = Mock(PersistentIndexedCache)
    def cacheAccess = Mock(TaskHistoryStore)
    def timeStampInspector = Mock(FileTimeStampInspector)
    def hash = Hashing.md5().hashString("hello", Charsets.UTF_8)
    def oldHash = Hashing.md5().hashString("hi", Charsets.UTF_8)
    def file = tmpDir.createFile("testfile")
    def fileSystem = TestFiles.fileSystem()
    CachingFileHasher hasher

    def setup() {
        file.write("some-content")
        1 * cacheAccess.createCache("fileHashes", _, _, _, _) >> cache
        hasher = new CachingFileHasher(target, cacheAccess, new StringInterner(), timeStampInspector, "fileHashes", fileSystem)
    }

    def hashesFileWhenHashNotCached() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.get(file.absolutePath) >> null
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == stat.length
            assert fileInfo.timestamp == stat.lastModified
        }
        0 * _._
    }

    def hashesFileWhenLengthHasChanged() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.get(file.absolutePath) >> new FileInfo(oldHash, 1024, stat.lastModified)
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == stat.length
            assert fileInfo.timestamp == stat.lastModified
        }
        0 * _._
    }

    def hashesFileWhenTimestampHasChanged() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.get(file.absolutePath) >> new FileInfo(oldHash, file.length(), 124)
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == stat.length
            assert fileInfo.timestamp == stat.lastModified
        }
        0 * _._
    }

    def doesNotHashFileWhenTimestampAndLengthHaveNotChanged() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.get(file.absolutePath) >> new FileInfo(hash, stat.length, stat.lastModified)
        0 * _._
    }

    def doesNotLoadCachedValueWhenTimestampCannotBeUsedToDetectChange() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> false
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == stat.length
            assert fileInfo.timestamp == stat.lastModified
        }
        0 * _._
    }

    def hashesFileDetails() {
        long lastModified = 123l
        long length = 321l
        def fileDetails = Mock(FileTreeElement)

        when:
        def result = hasher.hash(fileDetails)

        then:
        result == hash

        and:
        _ * fileDetails.file >> file
        _ * fileDetails.lastModified >> lastModified
        _ * fileDetails.size >> length
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, lastModified) >> true
        1 * cache.get(file.absolutePath) >> null
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == length
            assert fileInfo.timestamp == lastModified
        }
        0 * _._
    }

    def hashesGivenFileMetadataSnapshot() {
        long lastModified = 123l
        long length = 321l
        def fileDetails = DefaultFileMetadata.file(lastModified, length)

        when:
        def result = hasher.hash(file, fileDetails)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, lastModified) >> true
        1 * cache.get(file.absolutePath) >> null
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == length
            assert fileInfo.timestamp == lastModified
        }
        0 * _._
    }

    def hashesBackingFileWhenResourceIsBackedByFile() {
        def stat = fileSystem.stat(file)
        def resource = Mock(TextResource)

        when:
        def result = hasher.hash(resource)

        then:
        result == hash

        and:
        1 * resource.file >> file
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.get(file.absolutePath) >> new FileInfo(hash, stat.length, stat.lastModified)
        0 * _._
    }

    def hashesContentWhenResourceIsNotBackedByFile() {
        def resource = Mock(TextResource)

        when:
        def result = hasher.hash(resource)

        then:
        result == hash

        and:
        1 * resource.file >> null
        1 * target.hash(resource) >> hash
        0 * _._
    }
}
