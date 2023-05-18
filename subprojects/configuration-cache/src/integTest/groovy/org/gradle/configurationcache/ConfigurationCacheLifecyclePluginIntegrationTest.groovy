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

package org.gradle.configurationcache


import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

class ConfigurationCacheLifecyclePluginIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def 'buildDirectory is finalized when writing to the cache'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def buildDirName = 'my-build-dir'
        def buildDir = file(buildDirName)
        buildFile """

            // lifecycle-base plugin registers layout.buildDirectory as a build output
            plugins { id 'lifecycle-base' }

            abstract class MyBuildService implements $BuildService.name<${BuildServiceParameters.name}.None> {
                String buildDirFor(Project project) {
                    '$buildDir.name'
                }
            }

            def service = gradle.sharedServices.registerIfAbsent('my', MyBuildService, {})
            layout.buildDirectory.set(
                layout.projectDirectory.dir(
                    service.map {
                        // intentionally capture the `project` object to force a cc failure
                        // in case this gets serialized
                        it.buildDirFor(project)
                    }
                )
            )
        """

        when:
        buildDir.mkdir()
        configurationCacheRun 'clean'

        then:
        configurationCache.assertStateStored()

        and:
        buildDir.assertDoesNotExist()

        when:
        buildDir.mkdir()
        configurationCacheRun 'clean'

        then:
        configurationCache.assertStateLoaded()

        and:
        buildDir.assertDoesNotExist()
    }
}
