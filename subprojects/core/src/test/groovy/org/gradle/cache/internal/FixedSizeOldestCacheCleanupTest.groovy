/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.PersistentCache
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Subject(FixedSizeOldestCacheCleanup)
class FixedSizeOldestCacheCleanupTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def persistentCache = Mock(PersistentCache)
    def cleanupAction = new FixedSizeOldestCacheCleanup(new TestBuildOperationExecutor(),10)

    def "filters for cache entry files"() {
        expect:
        !cleanupAction.canBeDeleted("cache.properties")
        !cleanupAction.canBeDeleted("gc.properties")
        !cleanupAction.canBeDeleted("cache.lock")

        cleanupAction.canBeDeleted("0"*32)
        cleanupAction.canBeDeleted("ABCDEFABCDEFABCDEFABCDEFABCDEF00")
        cleanupAction.canBeDeleted("abcdefabcdefabcdefabcdefabcdef00")
    }

    def "finds eligible files"() {
        def cacheEntries = [
            createCacheEntry(1024), // 1KB
            createCacheEntry(1024*1024), // 1MB
            createCacheEntry(1024*1024*10), // 10MB
        ]
        cacheDir.file("cache.lock").touch()
        expect:
        def eligibleFiles = Arrays.asList(cleanupAction.findEligibleFiles(cacheDir))
        eligibleFiles.size() == cacheEntries.size()
        eligibleFiles.containsAll(cacheEntries)
    }

    def "finds files to delete when cache is larger than limit"() {
        def cacheEntries = [
            createCacheEntry(1024, 1000), // 1KB, newest file
            createCacheEntry(1024*1024, 500), // 1MB
            createCacheEntry(1024*1024*5, 250), // 5MB
            createCacheEntry(1024*1024*10, 0), // 10MB, oldest file
        ]
        expect:
        def filesToDelete = cleanupAction.findFilesToDelete(persistentCache, cacheEntries as File[])
        filesToDelete.size() == 1
        // we should only delete the last one
        filesToDelete[0] == cacheEntries.last()
    }

    def "finds no files to delete when cache is smaller than limit"() {
        def cacheEntries = [
            createCacheEntry(1024), // 1KB
            createCacheEntry(1024*1024), // 1MB
            createCacheEntry(1024*1024*5), // 5MB
        ]
        expect:
        def filesToDelete = cleanupAction.findFilesToDelete(persistentCache, cacheEntries as File[])
        filesToDelete.size() == 0
    }

    def "deletes files"() {
        def cacheEntries = [
            createCacheEntry(1024), // 1KB
            createCacheEntry(1024*1024), // 1MB
            createCacheEntry(1024*1024*5), // 5MB
        ]
        when:
        cleanupAction.cleanupFiles(persistentCache, cacheEntries)
        then:
        cacheEntries.each {
            it.assertDoesNotExist()
        }
    }

    def createCacheEntry(int size, int timestamp=0) {
        def cacheEntry = cacheDir.file(String.format("%032x", size))
        def data = new byte[size]
        new Random().nextBytes(data)
        cacheEntry.bytes = data
        cacheEntry.lastModified = timestamp
        return cacheEntry
    }
}
