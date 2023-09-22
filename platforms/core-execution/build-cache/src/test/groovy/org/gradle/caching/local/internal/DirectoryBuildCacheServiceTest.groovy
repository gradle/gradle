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

import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.cache.PersistentCache
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.resource.local.DefaultPathKeyFileStore
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
@CleanupTestDirectory
class DirectoryBuildCacheServiceTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def cacheDir = temporaryFolder.createDir("cache")
    def fileStore = new DefaultPathKeyFileStore(TestUtil.checksumService, cacheDir)
    def persistentCache = Mock(PersistentCache) {
        getBaseDir() >> cacheDir
        withFileLock(_) >> { Runnable r -> r.run() }
    }
    def tempFileStore = new DefaultBuildCacheTempFileStore(new DefaultTemporaryFileProvider(() -> cacheDir))
    def fileAccessTracker = Mock(FileAccessTracker)
    def service = new DirectoryBuildCacheService(fileStore, persistentCache, tempFileStore, fileAccessTracker, ".failed")
    def hashCode = "1234abcd"
    def key = Mock(BuildCacheKey) {
        getHashCode() >> hashCode
    }

    def "does not store partial result"() {
        when:
        service.store(key, new BuildCacheEntryWriter() {
            @Override
            void writeTo(OutputStream output) throws IOException {
                // Check that partial result file is created inside the cache directory
                def cacheDirFiles = cacheDir.listFiles()
                assert cacheDirFiles.length == 1

                def partialCacheFile = cacheDirFiles[0]
                assert partialCacheFile.name.startsWith(hashCode)
                assert partialCacheFile.name.endsWith(BuildCacheTempFileStore.PARTIAL_FILE_SUFFIX)

                output << "abcd"
                throw new RuntimeException("Simulated write error")
            }

            @Override
            long getSize() {
                return 100
            }
        })
        then:
        def ex = thrown RuntimeException
        ex.message == "Simulated write error"
        cacheDir.listFiles() as List == []
        1 * key.getHashCode() >> hashCode
        0 * fileAccessTracker.markAccessed(_)
    }

    def "marks file accessed when storing and loading locally"() {
        File cachedFile = null

        given:
        def originalFile = temporaryFolder.createFile("foo")
        originalFile.text = "bar"

        when:
        service.storeLocally(key, originalFile)

        then:
        1 * fileAccessTracker.markAccessed(_) >> { File file -> cachedFile = file }
        cachedFile.absolutePath.startsWith(cacheDir.absolutePath)

        when:
        service.loadLocally(key, { file ->
            assert file == cachedFile
            assert file.text == "bar"
        })

        then:
        1 * fileAccessTracker.markAccessed(cachedFile)
    }

    def "marks file accessed when storing and loading using writer and reader"() {
        File cachedFile = null

        when:
        service.store(key, new BuildCacheEntryWriter() {
            @Override
            void writeTo(OutputStream output) throws IOException {
                output.write("foo".getBytes())
            }

            @Override
            long getSize() {
                return 100
            }
        })

        then:
        1 * fileAccessTracker.markAccessed(_) >> { File file -> cachedFile = file }
        cachedFile.absolutePath.startsWith(cacheDir.absolutePath)

        when:
        def loaded = service.load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert input.text == "foo"
            }
        })

        then:
        1 * fileAccessTracker.markAccessed(cachedFile)
        loaded
    }
}
