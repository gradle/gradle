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

/**
 * This class tests that when configurations are used incorrectly - for instance, when a configuration where
 * {@link org.gradle.api.artifacts.Configuration#isCanBeResolved()} returns {@code false} is resolved - now (as of Gradle 8.0) throw exceptions
 * instead of merely warning.  We want to ensure that the {@code canBeResolved}, {@code canBeConsumed}, and {@code canBeDeclaredAgainst}
 * flags are set appropriately and consistently on all configurations prior to their usage towards one of these goals.
 */
class InvalidConfigurationResolutionIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        mavenRepo.module("module", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            allprojects {
                configurations {
                    implementation
                    compile.canBeDeclaredAgainst = false
                    compile.canBeConsumed = false
                    compile.canBeResolved = false
                    compileOnly.canBeResolved = false
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

    def "fail if a dependency is declared on a configuration which can not be declared against"() {
        given:
        buildFile << """
            dependencies {
                compile 'module:foo:1.0'
            }
        """

        when:
        fails 'help'

        then:
        failure.hasErrorOutput("Dependencies can not be declared against the `compile` configuration.")
    }

    def "fail if a dependency constraint is declared on a configuration which can not be declared against"() {
        given:
        buildFile << """
            dependencies {
                constraints {
                    compile 'module:foo:1.0'
                }
            }
        """

        when:
        fails 'help'

        then:
        failure.hasErrorOutput("Dependencies can not be declared against the `compile` configuration.")
    }

    def "fail if an artifact is declared on a configuration that can not be declared against"() {
        given:
        buildFile << """
            artifacts {
                compile file('some.jar')
            }
        """

        when:
        fails 'help'

        then:
        failure.hasErrorOutput("Dependencies can not be declared against the `compile` configuration.")
    }

    def "fail if a non-resolvable configuration is resolved"() {
        given:
        buildFile << """
            task resolve {
                doLast {
                    configurations.compileOnly.files
                }
            }
        """

        when:
        fails 'resolve'

        then:
        failure.hasErrorOutput("Dependencies can not be resolved using the `compileOnly` configuration.")
    }
}
