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

package org.gradle.api.internal.cache

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest


class CacheConfigurationsContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def "can configure caches via init script and execute multiple builds in a session"() {
        executer.requireOwnGradleUserHomeDir()

        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    cleanup = Cleanup.DISABLED
                    releasedWrappers.removeUnusedEntriesAfterDays = 10
                    snapshotWrappers.removeUnusedEntriesAfterDays = 5
                    downloadedResources.removeUnusedEntriesAfterDays = 10
                    createdResources.removeUnusedEntriesAfterDays = 5
                }
            }
        """

        when:
        buildFile << """
            task foo(type: Copy) {
                from 'foo'
                into layout.buildDir.dir('foo')
            }
        """
        file('foo').text = 'bar'

        then:
        succeeds("foo")
        file('build/foo/foo').text == 'bar'

        when:
        file('foo').text = 'baz'

        then:
        buildTriggeredAndSucceeded()
        file('build/foo/foo').text == 'baz'
    }
}
