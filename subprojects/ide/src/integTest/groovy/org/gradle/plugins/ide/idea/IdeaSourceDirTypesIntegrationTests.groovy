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

package org.gradle.plugins.ide.idea;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

import static org.gradle.plugins.ide.fixtures.IdeaFixtures.parseIml

class IdeaSourceDirTypesIntegrationTests extends AbstractIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "properly marks additional source sets created by test suites as test source"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
        """

        buildFile << """
            plugins {
                id 'java'
                id 'idea'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integTest(JvmTestSuite)
                }
            }
        """

        file('src/integTest/java').createDir()

        when:
        succeeds ":idea"

        then:
        result.assertTasksExecuted(":ideaModule", ":ideaProject", ":ideaWorkspace", ":idea")

        def moduleFixture = parseIml(file ( 'root.iml'))
        def sources = moduleFixture.getContent().getProperty("sources")
        def integTestSource = sources.find { s -> s.url == 'file://$MODULE_DIR$/src/integTest/java' }
        assert integTestSource.isTestSource
    }
}
