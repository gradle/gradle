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
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AbstractCacheCleanupTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def cleanableStore = Mock(CleanableStore) {
        getBaseDir() >> cacheDir
        getReservedCacheFiles() >> []
    }
    def progressMonitor = Mock(CleanupProgressMonitor)
    def deletedFiles = []

    def "deletes non-reserved matching files"() {
        def cacheEntries = [
            temporaryFolder.createFile("1"),
            temporaryFolder.createFile("2"),
            temporaryFolder.createFile("3"),
        ]

        when:
        cleanupAction(finder(cacheEntries), { it != cacheEntries[2] })
            .clean(cleanableStore, progressMonitor)

        then:
        1 * cleanableStore.getReservedCacheFiles() >> [cacheEntries[0]]
        1 * progressMonitor.incrementDeleted()
        1 * progressMonitor.incrementSkipped()
        cacheEntries[0].assertExists()
        cacheEntries[1].assertDoesNotExist()
        cacheEntries[2].assertExists()
        deletedFiles == [cacheEntries[1]]
    }

    def "can delete directories"() {
        given:
        def cacheEntry = cacheDir.file("subDir").createFile("somefile")
        cacheEntry.text = "delete me"

        when:
        cleanupAction(finder([cacheEntry.parentFile]), { true })
            .clean(cleanableStore, progressMonitor)

        then:
        1 * progressMonitor.incrementDeleted()
        cacheEntry.assertDoesNotExist()
        cacheEntry.parentFile.assertDoesNotExist()
        deletedFiles == [cacheEntry.parentFile]
    }

    def "deletes empty parent directories"() {
        given:
        def grandparent = cacheDir.createDir("a")
        def parent = grandparent.createDir("b")
        def file = parent.createFile("somefile")

        when:
        cleanupAction(finder([file]), { true })
            .clean(cleanableStore, progressMonitor)

        then:
        1 * progressMonitor.incrementDeleted()
        file.assertDoesNotExist()
        parent.assertDoesNotExist()
        grandparent.assertDoesNotExist()
        cacheDir.assertExists()
        deletedFiles == [file, parent, grandparent]
    }

    def "does not delete non-empty parent directories"() {
        given:
        def grandparent = cacheDir.createDir("a")
        def anotherFile = grandparent.createFile("anotherfile")
        def parent = grandparent.createDir("b")
        def file = parent.createFile("somefile")

        when:
        cleanupAction(finder([file]), { true })
            .clean(cleanableStore, progressMonitor)

        then:
        1 * progressMonitor.incrementDeleted()
        file.assertDoesNotExist()
        parent.assertDoesNotExist()
        grandparent.assertExists()
        anotherFile.assertExists()
        cacheDir.assertExists()
        deletedFiles == [file, parent]
    }

    FilesFinder finder(files) {
        Stub(FilesFinder) {
            find(_, _) >> { baseDir, filter ->
                assert filter instanceof NonReservedFileFilter
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

            @Override
            protected void handleDeletion(File file) {
                deletedFiles.add(file)
            }
        }
    }
}
