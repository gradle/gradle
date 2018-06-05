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

import org.gradle.api.specs.Spec
import org.gradle.cache.CleanableStore
import org.gradle.internal.time.CountdownTimer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AbstractCacheCleanupTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def cleanableStore = Mock(CleanableStore) {
        getBaseDir() >> cacheDir
        getReservedCacheFiles() >> []
    }
    def timer = Mock(CountdownTimer)

    def "deletes non-reserved matching files"() {
        def cacheEntries = [
            temporaryFolder.createFile("1"),
            temporaryFolder.createFile("2"),
            temporaryFolder.createFile("3"),
        ]

        when:
        cleanupAction(finder(cacheEntries), { it != cacheEntries[2] })
            .clean(cleanableStore, timer)

        then:
        (1.._) * cleanableStore.getReservedCacheFiles() >> [cacheEntries[0]]
        2 * timer.hasExpired()
        cacheEntries[0].assertExists()
        cacheEntries[1].assertDoesNotExist()
        cacheEntries[2].assertExists()
    }

    def "can delete directories"() {
        given:
        def cacheEntry = cacheDir.file("subDir").createFile("somefile")
        cacheEntry.text = "delete me"

        when:
        cleanupAction(finder([cacheEntry.parentFile]), { true })
            .clean(cleanableStore, timer)

        then:
        1 * timer.hasExpired()
        cacheEntry.assertDoesNotExist()
        cacheEntry.parentFile.assertDoesNotExist()
    }

    def "aborts cleanup when timer has expired"() {
        given:
        def cacheEntry = cacheDir.createFile("somefile")

        when:
        cleanupAction(finder([cacheEntry]), { true })
            .clean(cleanableStore, timer)

        then:
        1 * timer.hasExpired() >> true
        cacheEntry.assertExists()
    }

    FilesFinder finder(files) {
        Stub(FilesFinder) {
            find(_, _) >> { baseDir, filter ->
                assert filter instanceof NonReservedCacheFileFilter
                files.findAll { filter.accept(it) }
            }
        }
    }

    AbstractCacheCleanup cleanupAction(FilesFinder finder, Spec<File> spec) {
        new AbstractCacheCleanup(finder) {
            @Override
            protected boolean shouldDelete(File file) {
                return spec.isSatisfiedBy(file)
            }
        }
    }
}
