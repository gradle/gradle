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

class EclipseTestConfigurationsWithExternalDependenciesIntegrationTest extends AbstractEclipseTestSourcesIntegrationTest {

    def setup() {
        mavenRepo.module('org', 'lib').publish()
        buildFile << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in main source set dependency configurations are not marked with test classpath attribute"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org:lib:1.0'
            }
        """

        when:
        run 'eclipse'

        then:
        assertJarDependencyDoesNotHaveTestAttribute('lib-1.0.jar')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in test source set dependency configurations are marked with test classpath attribute"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'org:lib:1.0'
            }
        """

        when:
        run 'eclipse'

        then:
        assertJarDependencyHasTestAttribute('lib-1.0.jar')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in jvm test suites are marked with test classpath attribute"() {
        buildFile.text = """
            plugins {
                id 'jvm-test-suite'
            }
            ${buildFile.text}
        """
        buildFile << """
            testing {
                suites {
                    integration(JvmTestSuite) {
                        dependencies {
                             implementation 'org:lib:1.0'
                        }
                    }
                }
            }
        """

        when:
        run 'eclipse'

        then:
        assertJarDependencyHasTestAttribute('lib-1.0.jar')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies declared by the jvm test suite plugin are marked with test classpath attribute"() {
        buildFile.text = """
            plugins {
                id 'jvm-test-suite'
            }
            ${buildFile.text}
        """
        buildFile << """
            ${mavenCentralRepository()}

            testing {
                suites {
                    integration(JvmTestSuite) {
                        useJUnitJupiter()
                    }
                }
            }
        """

        when:
        run 'eclipse'

        then:
        assertJarDependencyHasTestAttribute('junit-platform-engine')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in custom source set dependency configurations are marked with test classpath attribute if the source set name contains the 'test' substring"() {
        given:
        buildFile << """
            sourceSets {
                functionalTest
            }

            dependencies {
                functionalTestImplementation 'org:lib:1.0'
            }
        """

        when:
        run 'eclipse'

        then:
        assertJarDependencyHasTestAttribute('lib-1.0.jar')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies in custom source set dependency configurations are not marked with test classpath attribute if the source set name does not contain the 'test' substring"() {
        given:
        buildFile << """
            sourceSets {
                integration
            }

            dependencies {
                integrationImplementation 'org:lib:1.0'
            }
        """

        when:
        run 'eclipse'

        then:
        assertJarDependencyDoesNotHaveTestAttribute('lib-1.0.jar')
    }

    @ToBeFixedForConfigurationCache
    def "can configure which source set dependency configurations contribute test dependencies to the classpath"() {
        given:
        mavenRepo.module('org', 'another').publish()
        buildFile << """
            configurations {
                integration
            }

            dependencies {
                integration 'org:lib:1.0'
                testImplementation 'org:another:1.0'
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
        assertJarDependencyHasTestAttribute('lib-1.0.jar')
        assertJarDependencyDoesNotHaveTestAttribute('another-1.0.jar')
    }

    @ToBeFixedForConfigurationCache
    def "dependencies present in test and non-test configurations are not marked with test classpath attribute"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org:lib:1.0'
                testImplementation 'org:lib:1.0'
            }
        """

        when:
        run 'eclipse'

        then:
        assertJarDependencyDoesNotHaveTestAttribute('lib-1.0.jar')
    }
}
