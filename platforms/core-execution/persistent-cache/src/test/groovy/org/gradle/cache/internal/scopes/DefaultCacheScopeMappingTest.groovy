/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.cache.internal.scopes

import org.gradle.cache.scopes.VersionStrategy
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class DefaultCacheScopeMappingTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def userHomeCaches = tmpDir.createDir("caches")
    def rootDir = tmpDir.createDir("root")
    def gradleVersion = Stub(GradleVersion) {
        getVersion() >> "version"
    }
    def mapping = new DefaultCacheScopeMapping(userHomeCaches, gradleVersion)

    def "null base dir maps to user home directory"() {
        expect:
        mapping.getBaseDirectory(null, "key", VersionStrategy.CachePerVersion) == userHomeCaches.file("version/key")
        mapping.getBaseDirectory(null, "key", VersionStrategy.SharedCache) == userHomeCaches.file("key")
    }

    def "uses specified base dir"() {
        expect:
        mapping.getBaseDirectory(rootDir, "key", VersionStrategy.CachePerVersion) == rootDir.file("version/key")
        mapping.getBaseDirectory(rootDir, "key", VersionStrategy.SharedCache) == rootDir.file("key")
    }

    def "can't use badly-formed key '#key'"() {
        when:
        mapping.getBaseDirectory(null, key, VersionStrategy.CachePerVersion)

        then:
        thrown(IllegalArgumentException)

        where:
        key << ["1.11", "1.2.3.4", "", "/", "..", "c:/some-dir", "\n", "a\\b", " no white space "]
    }

    def "can use well-formed key '#key'"() {
        when:
        mapping.getBaseDirectory(null, key, VersionStrategy.CachePerVersion)

        then:
        noExceptionThrown()

        where:
        key << ["abc", "a/b/c", "module-1.2"]
    }
}
