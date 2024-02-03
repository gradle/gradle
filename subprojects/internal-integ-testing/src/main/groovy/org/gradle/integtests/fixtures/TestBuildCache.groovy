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

    void deleteCacheEntry(String cacheKey) {
        def entry = getTestFileCacheEntry(cacheKey)
        if (entry.file.exists()) {
            entry.file.deleteDir()
        }
    }

    boolean hasCacheEntry(String cacheKey) {
        return getTestFileCacheEntry(cacheKey).file.exists()
    }

    TestCacheEntry getCacheEntry(String cacheKey) {
        def cacheEntry = getTestFileCacheEntry(cacheKey)
        assert cacheEntry.file.exists()
        return cacheEntry
    }

    boolean isEmpty() {
        listCacheFiles().empty
    }

    private TestFileCacheEntry getTestFileCacheEntry(String cacheKey) {
        return new TestFileCacheEntry(cacheKey, new TestFile(cacheDir, cacheKey))
    }

    interface TestCacheEntry {
        String getKey()
        String getMd5Hash()
        String getText()
        void setText(String text)
        byte[] getBytes()
        void setBytes(byte[] bytes)
        void copyBytesTo(TestFile file)
    }

    private class TestFileCacheEntry implements TestCacheEntry {

        String key
        TestFile file

        TestFileCacheEntry(String key, TestFile file) {
            this.key = key
            this.file = file
        }

        @Override
        String getKey() {
            return key
        }

        @Override
        String getMd5Hash() {
            return file.md5Hash
        }

        @Override
        String getText() {
            return file.text
        }

        @Override
        void setText(String text) {
            file.text = text
        }

        @Override
        byte[] getBytes() {
            return file.bytes
        }

        @Override
        void setBytes(byte[] bytes) {
            file.bytes = bytes
        }

        @Override
        void copyBytesTo(TestFile file) {
            this.file.copyTo(file)
        }
    }
}
