/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.initialization.GradleUserHomeDirProvider
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class GradleUserHomeCacheCleanupEnablementTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def gradleUserHomeProvider = Stub(GradleUserHomeDirProvider)
    def cacheCleanupEnablement = new GradleUserHomeCacheCleanupEnablement(gradleUserHomeProvider)

    def "allows cleanup for files in gradle user home"() {
        given:
        gradleUserHomeProvider.gradleUserHomeDirectory >> tmpDir.newFolder('foo')

        expect:
        cacheCleanupEnablement.isEnabledFor(directory(dir))

        where:
        dir << [
            'foo',
            'foo/bar',
            'foo/bar/baz'
        ]
    }

    def "does not allow cleanup for files not in gradle user home"() {
        given:
        gradleUserHomeProvider.gradleUserHomeDirectory >> tmpDir.newFolder('foo/bar')

        expect:
        !cacheCleanupEnablement.isEnabledFor(directory(dir))

        where:
        dir << [
            'foo',
            'foo/baz',
            'bar',
            'foo'
        ]
    }

    def "does not allow cleanup when caching is disabled"() {
        given:
        def guh = tmpDir.newFolder('foo')
        gradleUserHomeProvider.gradleUserHomeDirectory >> guh
        new File(guh, 'gradle.properties') << "${GradleUserHomeCacheCleanupEnablement.CACHE_CLEANUP_PROPERTY}=false"

        expect:
        !cacheCleanupEnablement.isEnabledFor(directory(dir))

        where:
        dir << [
            'foo',
            'foo/bar',
            'foo/bar/baz'
        ]
    }

    private File directory(String path) {
        def dir = new File(tmpDir.root, path)
        return dir.exists() ? dir : tmpDir.newFolder(path)
    }
}
