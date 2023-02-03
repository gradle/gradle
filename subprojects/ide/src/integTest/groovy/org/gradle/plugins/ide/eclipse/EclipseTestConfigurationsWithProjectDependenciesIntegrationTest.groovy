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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class EclipseTestConfigurationsWithProjectDependenciesIntegrationTest extends AbstractEclipseTestSourcesIntegrationTest {

    def setup() {
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
        """
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in main source set dependency configurations are not marked with test classpath attribute"() {
        given:
        file('a/build.gradle') << """
            dependencies {
                implementation project(':b')
            }
        """

        when:
        run 'eclipse'

        then:
        assertProjectDependencyDoesNotHaveTestAttribute('a', 'b')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in test source set dependency configurations are marked with test classpath attribute"() {
        given:
        file('a/build.gradle') << """
            dependencies {
                testImplementation project(':b')
            }
        """

        when:
        run 'eclipse'

        then:
        assertProjectDependencyHasTestAttribute('a', 'b')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in jvm test suites are marked with test classpath attribute"() {
        file('a/build.gradle') << """
            plugins {
                id 'jvm-test-suite'
            }

            testing {
                suites {
                    integration(JvmTestSuite) {
                        dependencies {
                             implementation project(':b')
                        }
                    }
                }
            }
        """

        when:
        run 'eclipse'

        then:
        assertProjectDependencyHasTestAttribute('a', 'b')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in custom source set dependency configurations are marked with test classpath attribute if the source set name contains the 'test' substring"() {
        given:
        file('a/build.gradle') << """
            sourceSets {
                functionalTest
            }

            dependencies {
                functionalTestImplementation project(':b')
            }
        """

        when:
        run 'eclipse'

        then:
        assertProjectDependencyHasTestAttribute('a', 'b')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in custom source set dependency configurations are not marked with test classpath attribute if the source set name does not contain the 'test' substring"() {
        given:
        file('a/build.gradle') << """
            sourceSets {
                integration
            }

            dependencies {
                integrationImplementation project(':b')
            }
        """

        when:
        run 'eclipse'

        then:
        assertProjectDependencyDoesNotHaveTestAttribute('a', 'b')
    }

    @ToBeFixedForConfigurationCache
    def "can configure which source set dependency configurations contribute test dependencies to the classpath"() {
        given:
        settingsFile << "\ninclude 'c'"
        file('c/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }
        """
        file('a/build.gradle') << """
            configurations {
                integration
            }

            dependencies {
                integration project(':b')
                testImplementation project(':c')
            }

            eclipse {
                classpath {
                    plusConfigurations += [configurations.integration]
                    testConfigurations = [configurations.integration]
                }
            }
        """

        when:
        run 'eclipse'

        then:
        assertProjectDependencyHasTestAttribute('a', 'b')
        assertProjectDependencyDoesNotHaveTestAttribute('a', 'c')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies present in test and non-test configurations are not marked with test classpath attribute"() {
        given:
        file('a/build.gradle') << """
            dependencies {
                implementation project(':b')
                testImplementation project(':b')
            }
        """

        when:
        run 'eclipse'

        then:
        assertProjectDependencyDoesNotHaveTestAttribute('a', 'b')
    }
}
