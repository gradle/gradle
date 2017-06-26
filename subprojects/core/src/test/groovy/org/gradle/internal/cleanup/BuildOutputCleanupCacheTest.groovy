/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.cleanup

import org.gradle.api.file.FileCollection
import org.gradle.api.invocation.Gradle
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildOutputCleanupCacheTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def cacheRepository = Mock(CacheRepository)
    def buildOutputDeleter = Mock(BuildOutputDeleter)
    def gradle = Mock(Gradle)
    def buildOutputCleanupRegistry = Mock(BuildOutputCleanupRegistry)
    def cache = Mock(PersistentCache)
    def buildOutputCleanupCache = new DefaultBuildOutputCleanupCache(cacheRepository, gradle, buildOutputDeleter, buildOutputCleanupRegistry) {
        @Override
        protected PersistentCache createCache() {
            cache
        }
    }

    def "only deletes outputs if marker file doesn't exist yet"() {
        def outputs = Mock(FileCollection)
        def cacheDir = tmpDir.file(".gradle/buildOutputCleanup")
        def markerFile = cacheDir.file("built.bin")

        expect:
        !markerFile.exists()

        when:
        buildOutputCleanupCache.cleanIfStale()

        then:
        // First cleanIfStale should delete outputs
        1 * cache.getBaseDir() >> cacheDir
        1 * cache.useCache((Runnable)_) >> { Runnable runnable ->
            runnable.run()
        }
        1 * buildOutputCleanupRegistry.outputs >> outputs
        1 * buildOutputDeleter.delete(outputs)
        1 * cache.close()
        0 * _
        markerFile.exists()

        when:
        buildOutputCleanupCache.cleanIfStale()

        then:
        // Second call to cleanIfStale should do nothing
        1 * cache.getBaseDir() >> cacheDir
        1 * cache.useCache((Runnable)_) >> { Runnable runnable ->
            runnable.run()
        }
        1 * cache.close()
        0 * _
        markerFile.exists()

        when:
        markerFile.delete()
        and:
        buildOutputCleanupCache.cleanIfStale()
        then:
        // Subsequent call to cleanIfStale after marker file is removed, causes a clean
        1 * cache.getBaseDir() >> cacheDir
        1 * cache.useCache((Runnable)_) >> { Runnable runnable ->
            runnable.run()
        }
        1 * buildOutputCleanupRegistry.outputs >> outputs
        1 * buildOutputDeleter.delete(outputs)
        1 * cache.close()
        0 * _
        markerFile.exists()
    }
}
