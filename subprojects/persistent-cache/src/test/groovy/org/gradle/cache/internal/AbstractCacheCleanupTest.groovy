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

class AbstractCacheCleanupTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def persistentCache = Mock(PersistentCache)
    def cleanupAction = new AbstractCacheCleanup(new TestBuildOperationExecutor()) {
        @Override
        protected List<File> findFilesToDelete(PersistentCache persistentCache, File[] filesEligibleForCleanup) {
            // pass through everything
            return Arrays.asList(filesEligibleForCleanup)
        }
    }

    def "filters for cache entry files"() {
        expect:
        !cleanupAction.canBeDeleted("cache.properties")
        !cleanupAction.canBeDeleted("gc.properties")
        !cleanupAction.canBeDeleted("cache.lock")

        cleanupAction.canBeDeleted("0"*32)
        cleanupAction.canBeDeleted("ABCDEFABCDEFABCDEFABCDEFABCDEF00")
        cleanupAction.canBeDeleted("abcdefabcdefabcdefabcdefabcdef00")
    }

    def "deletes files"() {
        def cacheEntries = [
            createCacheEntry(),
            createCacheEntry(),
            createCacheEntry(),
        ]
        when:
        cleanupAction.cleanupFiles(persistentCache, cacheEntries)
        then:
        cacheEntries.each {
            it.assertDoesNotExist()
        }
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
