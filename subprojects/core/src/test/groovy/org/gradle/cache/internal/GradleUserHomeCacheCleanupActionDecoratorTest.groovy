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
import org.gradle.internal.cache.MonitoredCleanupAction
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification


class GradleUserHomeCacheCleanupActionDecoratorTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    def delegateCleanupAction = Mock(CleanupAction)
    def delegateDirectoryCleanupAction = Mock(MonitoredCleanupAction)
    def gradleUserHomeProvider = Stub(GradleUserHomeDirProvider)
    def cacheCleanupDecorator = new GradleUserHomeCacheCleanupActionDecorator(gradleUserHomeProvider)

    def "wrapping allows cleanup when enabled"() {
        given:
        withCacheCleanupEnabledByDefault()

        when:
        cacheCleanupDecorator.decorate(delegateCleanupAction).clean(Mock(CleanableStore), Mock(CleanupProgressMonitor))

        then:
        1 * delegateCleanupAction.clean(_, _)
    }

    def "wrapping does not allow cleanup when cleanup is disabled"() {
        given:
        withCacheCleanupDisabled()

        when:
        cacheCleanupDecorator.decorate(delegateCleanupAction).clean(Mock(CleanableStore), Mock(CleanupProgressMonitor))

        then:
        0 * delegateCleanupAction.clean(_, _)
    }

    def "wrapping allows directory cleanup when enabled"() {
        given:
        withCacheCleanupEnabledByDefault()

        when:
        cacheCleanupDecorator.decorate(delegateDirectoryCleanupAction).execute(Mock(CleanupProgressMonitor))

        then:
        1 * delegateDirectoryCleanupAction.execute(_)
    }

    def "wrapping does not allow directory cleanup when cleanup is disabled"() {
        given:
        withCacheCleanupDisabled()

        when:
        cacheCleanupDecorator.decorate(delegateDirectoryCleanupAction).execute(Mock(CleanupProgressMonitor))

        then:
        0 * delegateDirectoryCleanupAction.execute(_)
    }

    private File setGradleUserHome(String gradleUserHomePath) {
        def guh = tmpDir.newFolder(gradleUserHomePath)
        gradleUserHomeProvider.gradleUserHomeDirectory >> guh
        return guh
    }

    private void withCacheCleanupEnabledByDefault() {
        setGradleUserHome('guh')
    }

    private void withCacheCleanupDisabled() {
        def guh = setGradleUserHome('guh')
        new File(guh, 'gradle.properties') << "${GradleUserHomeCacheCleanupActionDecorator.CACHE_CLEANUP_PROPERTY}=false"
    }
}
