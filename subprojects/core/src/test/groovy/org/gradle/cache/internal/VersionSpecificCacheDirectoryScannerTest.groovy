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

package org.gradle.cache.internal

import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@CleanupTestDirectory
class VersionSpecificCacheDirectoryScannerTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    TestFile cacheBaseDir = temporaryFolder.createDir(DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME)

    @Subject def versionSpecificCacheDirectoryService = new VersionSpecificCacheDirectoryScanner(cacheBaseDir)

    def "lists version-specific cache directories"() {
        given:
        cacheBaseDir.createDir("_foo")
        cacheBaseDir.createDir("1.2.3-rc-1")
        cacheBaseDir.createDir("0.9-20101220110000+1100")
        cacheBaseDir.createDir("2.3.4")
        cacheBaseDir.createDir("99_BAR")
        cacheBaseDir.createDir("ZZZZ")

        when:
        def directories = versionSpecificCacheDirectoryService.getExistingDirectories()

        then:
        directories as List == [
            new VersionSpecificCacheDirectory(cacheBaseDir.file("0.9-20101220110000+1100"), GradleVersion.version("0.9-20101220110000+1100")),
            new VersionSpecificCacheDirectory(cacheBaseDir.file("1.2.3-rc-1"), GradleVersion.version("1.2.3-rc-1")),
            new VersionSpecificCacheDirectory(cacheBaseDir.file("2.3.4"), GradleVersion.version("2.3.4"))
        ]
    }

    def "returns version-specific cache directory"() {
        expect:
        versionSpecificCacheDirectoryService.getDirectory(GradleVersion.version("1.2.3")) == cacheBaseDir.file("1.2.3")
    }

    def "returns base directory"() {
        expect:
        versionSpecificCacheDirectoryService.getBaseDir() == cacheBaseDir
    }
}
