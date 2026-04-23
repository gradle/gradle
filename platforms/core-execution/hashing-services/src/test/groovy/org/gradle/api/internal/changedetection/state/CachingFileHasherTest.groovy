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

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.CachingFileHasher.FileInfo
import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.IndexedCache
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CachingFileHasherTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def target = Mock(FileHasher)
    def cache = Mock(IndexedCache)
    def cacheAccess = Mock(CrossBuildFileHashCache)
    def timeStampInspector = Mock(FileTimeStampInspector)
    def hash = TestHashCodes.hashCodeFrom(0x0123)
    def oldHash = TestHashCodes.hashCodeFrom(0x0321)
    def file = tmpDir.createFile("testfile")
    def fileSystem = TestFiles.fileSystem()
    def statisticsCollector = Mock(FileHasherStatistics.Collector)
    CachingFileHasher hasher

    def setup() {
        file.write("some-content")
        1 * cacheAccess.createIndexedCache({ it.cacheName == "fileHashes"  }, _, _) >> cache
        hasher = new CachingFileHasher(target, cacheAccess, new StringInterner(), timeStampInspector, "fileHashes", fileSystem, 1000, statisticsCollector)
    }

    def "hashes file when hash not cached"() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.getIfPresent(file.absolutePath) >> null
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == stat.length
            assert fileInfo.timestamp == stat.lastModified
        }
        1 * statisticsCollector.reportFileHashed(file.length())
        0 * _
    }

    def "hashes file when length has changed"() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.getIfPresent(file.absolutePath) >> new FileInfo(oldHash, 1024, stat.lastModified)
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == stat.length
            assert fileInfo.timestamp == stat.lastModified
        }
        1 * statisticsCollector.reportFileHashed(file.length())
        0 * _
    }

    def "hashes file when timestamp has changed"() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.getIfPresent(file.absolutePath) >> new FileInfo(oldHash, file.length(), 124)
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == stat.length
            assert fileInfo.timestamp == stat.lastModified
        }
        1 * statisticsCollector.reportFileHashed(file.length())
        0 * _
    }

    def "does not hash file when timestamp and length have not changed"() {
        def stat = fileSystem.stat(file)

        when:
        def result = hasher.hash(file)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, stat.lastModified) >> true
        1 * cache.getIfPresent(file.absolutePath) >> new FileInfo(hash, stat.length, stat.lastModified)
        0 * _
    }

    def "does not load cached value when timestamp cannot be used to detect change"() {
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
        1 * statisticsCollector.reportFileHashed(file.length())
        0 * _
    }

    def "hashes given file length and last modified"() {
        long lastModified = 123l
        long length = 321l
        def fileMetadata = DefaultFileMetadata.file(lastModified, length, AccessType.DIRECT)

        when:
        def result = hasher.hash(file, fileMetadata.length, fileMetadata.lastModified)

        then:
        result == hash

        and:
        1 * timeStampInspector.timestampCanBeUsedToDetectFileChange(file.absolutePath, lastModified) >> true
        1 * cache.getIfPresent(file.absolutePath) >> null
        1 * target.hash(file) >> hash
        1 * cache.put(file.absolutePath, _) >> { String key, FileInfo fileInfo ->
            assert fileInfo.hash == hash
            assert fileInfo.length == length
            assert fileInfo.timestamp == lastModified
        }
        1 * statisticsCollector.reportFileHashed(length)
        0 * _
    }
}
