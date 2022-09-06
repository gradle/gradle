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

import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupAction
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.initialization.GradleUserHomeDirProvider
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class GradleUserHomeCacheCleanupEnablementTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def delegateCleanupAction = Mock(CleanupAction)
    def gradleUserHomeProvider = Stub(GradleUserHomeDirProvider)
    def cacheCleanupEnablement = new GradleUserHomeCacheCleanupEnablement(gradleUserHomeProvider)

    def "allows cleanup when enabled"() {
        given:
        enableCacheCleanup('foo')

        expect:
        cacheCleanupEnablement.isEnabled()
    }

    def "disallows cleanup when disabled"() {
        given:
        disableCacheCleanup('foo')

        expect:
        !cacheCleanupEnablement.isEnabled()
    }

    def "wrapping allows cleanup when enabled"() {
        given:
        enableCacheCleanup('foo')

        when:
        cacheCleanupEnablement.create(delegateCleanupAction).clean(Mock(CleanableStore), Mock(CleanupProgressMonitor))

        then:
        1 * delegateCleanupAction.clean(_, _)
    }

    def "wrapping does not allow cleanup for files in gradle user home when cleanup is disabled"() {
        given:
        disableCacheCleanup('foo')

        when:
        cacheCleanupEnablement.create(delegateCleanupAction).clean(Mock(CleanableStore), Mock(CleanupProgressMonitor))

        then:
        0 * delegateCleanupAction.clean(_, _)
    }

    private File setGradleUserHome(String gradleUserHomePath) {
        def guh = tmpDir.newFolder(gradleUserHomePath)
        gradleUserHomeProvider.gradleUserHomeDirectory >> guh
        return guh
    }

    private void enableCacheCleanup(String gradleUserHomePath) {
        setGradleUserHome(gradleUserHomePath)
    }

    private void disableCacheCleanup(String gradleUserHomePath) {
        def guh = setGradleUserHome(gradleUserHomePath)
        new File(guh, 'gradle.properties') << "${GradleUserHomeCacheCleanupEnablement.CACHE_CLEANUP_PROPERTY}=false"
    }
}
