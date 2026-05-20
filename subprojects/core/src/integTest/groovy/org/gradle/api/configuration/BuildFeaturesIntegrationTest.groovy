/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.configuration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class BuildFeaturesIntegrationTest extends AbstractIntegrationSpec {

    def "can print operational parameters via an internal option"() {
        when:
        run "help", "-q", "-Dorg.gradle.internal.operations.verbose.parameters=true"

        then:
        def cc = GradleContextualExecuter.isConfigCache()
        def ip = GradleContextualExecuter.isIsolatedProjects()
        outputContains("Operational build model parameters: {")
        outputContains("configurationCache=$cc")
        outputContains("isolatedProjects=$ip")
        outputContains("vintage=${!cc && !ip}")
    }

    def "can inject service into settings plugin"() {
        settingsFile """
            abstract class SomePlugin implements Plugin<Settings> {
                @Inject
                abstract BuildFeatures getBuildFeatures()

                void apply(Settings s) {
                    if (buildFeatures != null) println("buildFeatures are available")
                }
            }

            apply plugin: SomePlugin
        """

        when:
        run "help"

        then:
        outputContains("buildFeatures are available")
    }

    def "can inject service into project plugin"() {
        buildFile """
            abstract class SomePlugin implements Plugin<Project> {
                @Inject
                abstract BuildFeatures getBuildFeatures()

                void apply(Project p) {
                    if (buildFeatures != null) println("buildFeatures are available")
                }
            }

            apply plugin: SomePlugin
        """

        when:
        run "help"

        then:
        outputContains("buildFeatures are available")
    }
}
