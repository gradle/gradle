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

import groovy.transform.CompileStatic
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

class ConfigurationCacheLifecyclePluginIntegrationTest  extends AbstractConfigurationCacheIntegrationTest {

    def 'buildDirectory is finalized when writing to the cache'() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        def buildDirName = 'my-build-dir'
        def buildDir = file(buildDirName)
        buildFile """

            plugins { id 'lifecycle-base' }

            @$CompileStatic.name
            abstract class MyBuildService implements $BuildService.name<${BuildServiceParameters.name}.None> {
                String myBuildDir(Project project) {
                    return '$buildDir.name'
                }
            }

            @$CompileStatic.name
            final class MyTransformer implements Transformer<String, MyBuildService> {
                private final Project project

                MyTransformer(Project project) {
                    this.project = project
                }

                String transform(MyBuildService it) {
                    return it.myBuildDir(project)
                }
            }

            // lifecycle-base plugin registers layout.buildDirectory as a build output
            def service = gradle.sharedServices.registerIfAbsent('my', MyBuildService, {})
            layout.buildDirectory.set(
                layout.projectDirectory.dir(
                    service.map(new MyTransformer(project))
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
