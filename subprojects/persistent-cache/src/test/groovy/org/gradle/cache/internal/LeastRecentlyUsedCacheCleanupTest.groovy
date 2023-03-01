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

import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.internal.resource.local.ModificationTimeFileAccessTimeJournal
import org.gradle.internal.time.TimestampSuppliers
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

class LeastRecentlyUsedCacheCleanupTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def cleanableStore = Stub(CleanableStore) {
        getBaseDir() >> cacheDir
    }
    def fileAccessTimeJournal = Spy(ModificationTimeFileAccessTimeJournal)
    def progressMonitor = Stub(CleanupProgressMonitor)
    @Subject def cleanupAction = new LeastRecentlyUsedCacheCleanup(
        new SingleDepthFilesFinder(1), fileAccessTimeJournal, TimestampSuppliers.daysAgo(1))

    def "finds files to delete when files are old"() {
        given:
        long now = System.currentTimeMillis()
        long fiveDaysAgo = now - TimeUnit.DAYS.toMillis(5)
        def cacheEntries = [
            createCacheEntry(now),
            createCacheEntry(now),
            createCacheEntry(now),
            createCacheEntry(fiveDaysAgo),
        ]

        when:
        cleanupAction.clean(cleanableStore, progressMonitor)

        then:
        cacheEntries[0].assertExists()
        cacheEntries[1].assertExists()
        cacheEntries[2].assertExists()
        cacheEntries[3].assertDoesNotExist()
        1 * fileAccessTimeJournal.deleteLastAccessTime(cacheEntries[3])
    }

    def "finds no files to delete when files are new"() {
        given:
        long now = System.currentTimeMillis()
        def cacheEntries = [
            createCacheEntry(now),
            createCacheEntry(now - TimeUnit.MINUTES.toMillis(15)),
            createCacheEntry(now - TimeUnit.HOURS.toMillis(5)),
        ]

        when:
        cleanupAction.clean(cleanableStore, progressMonitor)

        then:
        cacheEntries[0].assertExists()
        cacheEntries[1].assertExists()
        cacheEntries[2].assertExists()
        0 * fileAccessTimeJournal.deleteLastAccessTime(_)
    }

    private Random r = new Random()
    def createCacheEntry(long timestamp) {
        def cacheEntry = cacheDir.file(String.format("%032x", r.nextInt()))
        def data = new byte[1024]
        r.nextBytes(data)
        cacheEntry.bytes = data
        cacheEntry.lastModified = timestamp
        return cacheEntry
    }
}
