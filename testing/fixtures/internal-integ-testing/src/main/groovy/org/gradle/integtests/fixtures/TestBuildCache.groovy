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

package org.gradle.integtests.fixtures

import org.gradle.caching.local.internal.BuildCacheTempFileStore
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile

class TestBuildCache {
    private final TestFile cacheDir

    TestBuildCache(File cacheDir) {
        this.cacheDir = new TestFile(cacheDir)
    }

    def localCacheConfiguration(boolean push = true) {
        """
            buildCache {
                local {
                    directory = '${cacheDir.absoluteFile.toURI()}'
                    push = $push
                }
            }
        """
    }

    def remoteCacheConfiguration(boolean push = true) {
        """
            buildCache {
                remote(DirectoryBuildCache) {
                    directory = '${cacheDir.absoluteFile.toURI()}'
                    push = $push
                }
            }
        """
    }

    TestFile getCacheDir() {
        cacheDir
    }

    TestFile gcFile() {
        cacheDir.file("gc.properties")
    }

    List<TestFile> listCacheTempFiles() {
        cacheDir.listFiles().findAll { it.name.endsWith(BuildCacheTempFileStore.PARTIAL_FILE_SUFFIX) }.sort()
    }

    List<TestFile> listCacheFailedFiles() {
        cacheDir.listFiles().findAll { it.name.endsWith(DirectoryBuildCacheServiceFactory.FAILED_READ_SUFFIX) }.sort()
    }

    List<TestFile> listCacheFiles() {
        cacheDir.listFiles().findAll { it.name ==~ /\p{XDigit}{${Hashing.defaultFunction().hexDigits}}/ }.sort()
    }

    boolean isEmpty() {
        listCacheFiles().empty
    }

    TestFile cacheArtifact(String cacheKey) {
        new TestFile(cacheDir, cacheKey)
    }
}
