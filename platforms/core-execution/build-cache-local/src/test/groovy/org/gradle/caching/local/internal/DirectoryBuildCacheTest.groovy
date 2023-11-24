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

package org.gradle.caching.local.internal


import org.gradle.cache.PersistentCache
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.resource.local.DefaultPathKeyFileStore
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files

@UsesNativeServices
@CleanupTestDirectory
class DirectoryBuildCacheTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def cacheDir = temporaryFolder.createDir("cache")
    def fileStore = new DefaultPathKeyFileStore(TestUtil.checksumService, cacheDir)
    def persistentCache = Mock(PersistentCache) {
        getBaseDir() >> cacheDir
        withFileLock(_) >> { Runnable r -> r.run() }
    }
    def tempFileStore = new DefaultBuildCacheTempFileStore({ prefix, suffix -> Files.createTempFile(cacheDir.toPath(), prefix, suffix).toFile() })
    def fileAccessTracker = Mock(FileAccessTracker)
    def cache = new DirectoryBuildCache(fileStore, persistentCache, tempFileStore, fileAccessTracker, ".failed")
    def key = TestHashCodes.hashCodeFrom(12345678)
    def hashCode = key.toString()

    def "does not store partial result"() {
        when:
        cache.store(key) { output ->
            // Check that partial result file is created inside the cache directory
            def cacheDirFiles = cacheDir.listFiles()
            assert cacheDirFiles.length == 1

            def partialCacheFile = cacheDirFiles[0]
            assert partialCacheFile.name.startsWith(hashCode)
            assert partialCacheFile.name.endsWith(BuildCacheTempFileStore.PARTIAL_FILE_SUFFIX)

            output << "abcd"
            throw new RuntimeException("Simulated write error")
        }

        then:
        def ex = thrown RuntimeException
        ex.message == "Simulated write error"
        cacheDir.listFiles() as List == []
        0 * fileAccessTracker.markAccessed(_)
    }

    def "marks file accessed when storing and loading locally"() {
        File cachedFile = null

        given:
        def originalFile = temporaryFolder.createFile("foo")
        originalFile.text = "bar"

        when:
        cache.storeLocally(key, originalFile)

        then:
        1 * fileAccessTracker.markAccessed(_) >> { File file -> cachedFile = file }
        cachedFile.absolutePath.startsWith(cacheDir.absolutePath)

        when:
        cache.loadLocally(key, { file ->
            assert file == cachedFile
            assert file.text == "bar"
        })

        then:
        1 * fileAccessTracker.markAccessed(cachedFile)
    }

    def "marks file accessed when storing and loading using writer and reader"() {
        File cachedFile = null

        when:
        cache.store(key) { output ->
            output.write("foo".getBytes())
        }

        then:
        1 * fileAccessTracker.markAccessed(_) >> { File file -> cachedFile = file }
        cachedFile.absolutePath.startsWith(cacheDir.absolutePath)

        when:
        def loaded = cache.load(key) { input ->
            assert input.text == "foo"
        }
        then:
        1 * fileAccessTracker.markAccessed(cachedFile)
        loaded
    }
}
