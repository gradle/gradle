/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecatedConfigurationsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.expectDeprecationWarning()

        mavenRepo.module("module", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            allprojects {
                configurations {
                    implementation
                    compile.deprecateForDeclaration("implementation")
                    compile.deprecateForConsumption("compileElements")
                    compile.deprecateForResolution("compileClasspath")
                    compileOnly.deprecateForConsumption("compileElements")
                    compileOnly.deprecateForResolution("compileClasspath")
                    apiElements {
                        canBeConsumed = true
                        canBeResolved = false
                        extendsFrom compile
                        extendsFrom compileOnly
                        extendsFrom implementation
                    }
                    compileClasspath {
                        canBeConsumed = false
                        canBeResolved = true
                        extendsFrom compile
                        extendsFrom compileOnly
                        extendsFrom implementation
                    }
                }
            }
        """
    }

    def "warn if a dependency is declared on a deprecated configuration"() {
        given:
        buildFile << """
            dependencies {
                compile 'module:foo:1.0'
            }
        """

        when:
        succeeds 'help'

        then:
        outputContains "The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead."
    }

    def "warn if a dependency constraint is declared on a deprecated configuration"() {
        given:
        buildFile << """
            dependencies {
                constraints {
                    compile 'module:foo:1.0'
                }
            }
        """

        when:
        succeeds 'help'

        then:
        outputContains "The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead."
    }

    def "warn if an artifact is declared on a configuration that is fully deprecated"() {
        given:
        buildFile << """
            artifacts {
                compile file('some.jar')
            }
        """

        when:
        succeeds 'help'

        then:
        outputContains "The compile configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 7.0. Please use the implementation or compileElements configuration instead."
    }

    def "warn if a deprecated configuration is resolved"() {
        given:
        buildFile << """
            task resolve {
                doLast {
                    configurations.compileOnly.files
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        outputContains "The compileOnly configuration has been deprecated for resolution. This will fail with an error in Gradle 7.0. Please resolve the compileClasspath configuration instead."
    }

    def "warn if a deprecated project configuration is consumed"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(path: ':a', configuration: 'compileOnly')
                }
                
                task resolve {
                    doLast {
                        configurations.compileClasspath.files
                    }
                }
            }
        """

        when:
        succeeds ':b:resolve'

        then:
        outputContains "The compileOnly configuration has been deprecated for consumption. This will fail with an error in Gradle 7.0. Please use attributes to consume the compileElements configuration instead."
    }

    def "warn if a deprecated project configuration is consumed directly"() {
        // this is testing legacy code that we can/should probably get rid of
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(path: ':a', configuration: 'compileOnly')
                }
                
                task resolve {
                    doLast {
                        configurations.implementation.dependencies[0].resolve()
                    }
                }
            }
        """

        when:
        succeeds ':b:resolve', ':b:dependencies'

        then:
        outputContains "The compileOnly configuration has been deprecated for consumption. This will fail with an error in Gradle 7.0. Please use attributes to consume the compileElements configuration instead."
    }
}
