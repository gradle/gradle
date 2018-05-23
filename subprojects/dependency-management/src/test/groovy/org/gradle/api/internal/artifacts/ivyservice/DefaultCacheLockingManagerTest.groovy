/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject

class DefaultCacheLockingManagerTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def cacheRepository = new DefaultCacheRepository(null, new InMemoryCacheFactory())
    def resourcesDir = temporaryFolder.createDir("resources")
    def artifactCacheMetadata = Stub(ArtifactCacheMetadata) {
        getCacheDir() >> temporaryFolder.getTestDirectory()
        getExternalResourcesStoreDirectory() >> resourcesDir
    }

    @Subject @AutoCleanup
    def cacheLockingManager = new DefaultCacheLockingManager(cacheRepository, artifactCacheMetadata)

    def "cleans up resources"() {
        given:
        def file1 = resourcesDir.createDir("1/abc").createFile("test.txt")
        def file2 = resourcesDir.createDir("1/xyz").createFile("test.txt")
        def file3 = resourcesDir.createDir("2/uvw").createFile("test.txt")
        file2.parentFile.lastModified = 0
        file3.parentFile.lastModified = 0

        when:
        cacheLockingManager.close()

        then:
        file1.assertExists()
        file2.assertDoesNotExist()
        file3.assertDoesNotExist()
    }
}
