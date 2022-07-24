/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ProjectLifecycleFixture
import org.junit.Rule
import spock.lang.Issue

class ConfigurationOnDemandPluginsIntegrationTest extends AbstractIntegrationSpec {
    @Rule ProjectLifecycleFixture fixture = new ProjectLifecycleFixture(executer, temporaryFolder)

    @Issue('GRADLE-3534')
    def "configures only requested projects when the #plugin plugin is applied"() {
        given:
        multiProjectBuild('multi', ['a', 'b']) {
            buildFile << """
                allprojects {
                    apply plugin: '${plugin}'
                }
                subprojects {
                    apply plugin: 'java'
                }
            """.stripIndent()

            gradlePropertiesFile << "org.gradle.configureondemand=true"
        }

        when:
        run ':a:build'

        then:
        fixture.assertProjectsConfigured(':', ':a')

        where:
        plugin << ['idea', 'eclipse']
    }
}
