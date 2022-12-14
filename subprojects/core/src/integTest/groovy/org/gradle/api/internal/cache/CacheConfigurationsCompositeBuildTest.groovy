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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class CacheConfigurationsCompositeBuildTest extends AbstractIntegrationSpec {
    def "can configure cache retention with a composite build"() {
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

        file('foo').createDir().createFile('settings.gradle')
        file('bar').createDir().createFile('settings.gradle')
        settingsFile << """
            includeBuild('foo')
            includeBuild('bar')
        """

        expect:
        succeeds('help')

        and:
        succeeds(':foo:tasks')

        and:
        succeeds(':bar:tasks')
    }
}
