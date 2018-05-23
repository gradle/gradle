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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

import static org.gradle.cache.internal.AbstractCacheCleanup.DIRECT_CHILDREN

@Subject(FixedAgeOldestCacheCleanup)
class FixedAgeOldestCacheCleanupTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def persistentCache = Mock(PersistentCache)
    def cleanupAction = new FixedAgeOldestCacheCleanup(DIRECT_CHILDREN, 1)

    def "finds files to delete when files are old"() {
        long now = System.currentTimeMillis()
        long fiveDaysAgo = now - TimeUnit.DAYS.toMillis(5)
        def cacheEntries = [
            createCacheEntry(1024, now),
            createCacheEntry(1024, now),
            createCacheEntry(1024, now),
            createCacheEntry(1024, fiveDaysAgo),
        ]
        expect:
        def filesToDelete = cleanupAction.findFilesToDelete(persistentCache, cacheEntries as File[])
        filesToDelete.size() == 1
        // we should only delete the last one
        filesToDelete[0] == cacheEntries.last()
    }

    def "finds no files to delete when files are new"() {
        long now = System.currentTimeMillis()
        def cacheEntries = [
            createCacheEntry(1024, now),
            createCacheEntry(1024, now - TimeUnit.MINUTES.toMillis(15)),
            createCacheEntry(1024, now - TimeUnit.HOURS.toMillis(5)),
        ]
        expect:
        def filesToDelete = cleanupAction.findFilesToDelete(persistentCache, cacheEntries as File[])
        filesToDelete.size() == 0
    }

    private Random r = new Random()
    def createCacheEntry(int size=1024, long timestamp=0) {
        def cacheEntry = cacheDir.file(String.format("%032x", r.nextInt()))
        def data = new byte[size]
        r.nextBytes(data)
        cacheEntry.bytes = data
        cacheEntry.lastModified = timestamp
        return cacheEntry
    }
}
