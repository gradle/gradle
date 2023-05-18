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

import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.cache.CacheResourceConfigurationInternal
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory
import org.gradle.cache.internal.UsedGradleVersions
import org.gradle.internal.resource.local.ModificationTimeFileAccessTimeJournal
import org.gradle.internal.time.TimestampSuppliers
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject

class DefaultArtifactCacheLockingAccessCoordinatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def cacheRepository = new DefaultUnscopedCacheBuilderFactory(null, new TestInMemoryCacheFactory())
    def cacheDir = temporaryFolder.createDir(CacheLayout.ROOT.key)
    def resourcesDir = cacheDir.createDir(CacheLayout.RESOURCES.key)
    def filesDir = cacheDir.createDir(CacheLayout.FILE_STORE.key)
    def metaDataDir = cacheDir.createDir(CacheLayout.META_DATA.key)
    def artifactCacheMetadata = Stub(ArtifactCacheMetadata) {
        getCacheDir() >> cacheDir
        getExternalResourcesStoreDirectory() >> resourcesDir
        getFileStoreDirectory() >> filesDir
        getMetaDataStoreDirectory() >> metaDataDir.file("descriptors")
    }
    def fileAccessTimeJournal = new ModificationTimeFileAccessTimeJournal()
    def usedGradleVersions = Stub(UsedGradleVersions)
    def cacheConfigurations = Stub(CacheConfigurationsInternal) {
        getDownloadedResources() >> Stub(CacheResourceConfigurationInternal) {
            //noinspection UnnecessaryQualifiedReference
            getRemoveUnusedEntriesOlderThanAsSupplier() >> TimestampSuppliers.daysAgo(CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES)
        }
    }

    @Subject @AutoCleanup
    def cacheLockingManager = new WritableArtifactCacheLockingAccessCoordinator(cacheRepository, artifactCacheMetadata, fileAccessTimeJournal, usedGradleVersions, cacheConfigurations)

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

    def "cleans up files"() {
        given:
        def file1 = filesDir.createDir("group1/artifact1/1.0/abc").createFile("my.pom")
        def file2 = filesDir.createDir("group1/artifact1/1.0/xyz").createFile("my.jar")
        def file3 = filesDir.createDir("group1/artifact1/2.0/uvw").createFile("some.pom")
        file2.parentFile.lastModified = 0
        file3.parentFile.lastModified = 0

        when:
        cacheLockingManager.close()

        then:
        file1.assertExists()
        file2.assertDoesNotExist()
        file3.assertDoesNotExist()
    }

    def "deletes old versions of cache dir"() {
        given:
        def oldCacheDir = cacheDir.getParentFile().createDir("modules-1")

        when:
        cacheLockingManager.close()

        then:
        oldCacheDir.assertDoesNotExist()
    }

    def "deletes old versions of metadata dir"() {
        given:
        def oldCacheDir = metaDataDir.getParentFile().createDir("metadata-2.56")

        when:
        cacheLockingManager.close()

        then:
        oldCacheDir.assertDoesNotExist()
    }
}
