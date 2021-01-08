/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants

class EclipseTestDependenciesIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "project dependency does not leak test sources"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        Node projectDependency = classpath('b').entries.find { it.attribute('path') == '/a'}
        projectDependency.attributes.attribute.find { it.@name == EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY && it.@value == 'true' }
    }

    @ToBeFixedForConfigurationCache
    def "project dependency pointing to test fixture project exposes test sources"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
                id 'java-test-fixtures'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        Node projectDependency = classpath('b').entries.find { it.attribute('path') == '/a'}
        !projectDependency.attributes.attribute.find { it.@name == EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY && it.@value == 'true' }
    }

    @ToBeFixedForConfigurationCache
    def "can configure test sources via eclipse classpath"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            eclipse {
                classpath {
                    containsTestFixtures = true
                }
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        Node projectDependency = classpath('b').entries.find { it.attribute('path') == '/a'}
        projectDependency.attributes.attribute.find { it.@name == EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY && it.@value == 'false' }
    }

    @ToBeFixedForConfigurationCache
    def "classpath configuration has precendence for test dependencies"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-test-fixtures'
                id 'java-library'
            }

            eclipse {
                classpath {
                    containsTestFixtures = false
                }
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        Node projectDependency = classpath('b').entries.find { it.attribute('path') == '/a'}
        projectDependency.attributes.attribute.find { it.@name == EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY && it.@value == 'true' }
    }
}
