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

package org.gradle.integtests.fixtures

import groovy.transform.SelfType
import org.apache.commons.io.FileUtils
import org.gradle.caching.local.internal.BuildCacheTempFileStore
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before

import java.util.concurrent.TimeUnit

@SelfType(AbstractIntegrationSpec)
trait DirectoryBuildCacheFixture extends BuildCacheFixture {
    private TestFile cacheDir

    @Before
    void setupCacheDirectory() {
        // Make sure cache dir is empty for every test execution
        cacheDir = temporaryFolder.file("cache-dir").deleteDir().createDir()
        settingsFile << localCacheConfiguration()
    }

    def localCacheConfiguration() {
        """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = '${cacheDir.absoluteFile.toURI()}'
                }
            }
        """
    }

    TestFile getCacheDir() {
        cacheDir
    }

    void cleanLocalBuildCache() {
        listCacheFiles().each { file ->
            println "Deleting cache entry: $file"
            FileUtils.forceDelete(file)
        }
    }

    TestFile gcFile() {
        cacheDir.file("gc.properties")
    }

    void cleanupBuildCacheNow() {
        gcFile().assertIsFile()
        gcFile().lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60)
    }

    List<TestFile> listCacheTempFiles() {
        cacheDir.listFiles().findAll { it.name.endsWith(BuildCacheTempFileStore.PARTIAL_FILE_SUFFIX) }.sort()
    }

    List<TestFile> listCacheFiles() {
        listCacheFiles(cacheDir)
    }

    List<TestFile> listCacheFailedFiles() {
        cacheDir.listFiles().findAll { it.name.endsWith(DirectoryBuildCacheServiceFactory.FAILED_READ_SUFFIX) }.sort()
    }

    static List<TestFile> listCacheFiles(TestFile cacheDir) {
        cacheDir.listFiles().findAll { it.name ==~ /\p{XDigit}{32}/ }.sort()
    }

    TestFile localCacheArtifact(String cacheKey) {
        new TestFile(cacheDir, cacheKey)
    }
}
