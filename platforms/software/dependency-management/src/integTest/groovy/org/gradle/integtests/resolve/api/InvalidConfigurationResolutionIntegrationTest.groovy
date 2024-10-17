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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * This class tests that when configurations are used incorrectly - for instance, when a configuration where
 * {@link org.gradle.api.artifacts.Configuration#isCanBeResolved()} returns {@code false} is resolved - now (as of Gradle 8.0) throw exceptions
 * instead of merely warning.  We want to ensure that the {@code canBeResolved}, {@code canBeConsumed}, and {@code canBeDeclared}
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
                    compile.canBeDeclared = false
                    compile.canBeConsumed = false
                    compile.canBeResolved = false
                    compileOnly.canBeResolved = false
                    apiElements {
                        assert canBeConsumed
                        canBeResolved = false
                        extendsFrom compile
                        extendsFrom compileOnly
                        extendsFrom implementation
                    }
                    compileClasspath {
                        canBeConsumed = false
                        assert canBeResolved
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

    def "failures adding dependencies to resolvable configurations emitted from configure closure are reported"() {
        given:
        buildFile << """
            configurations.resolvable('foo') {
                dependencies.add(project.dependencies.create('com.example:foo:4.2'))
            }

            // Realize the configuration
            configurations.foo
        """

        when:
        fails("help")

        then:
        failure.hasErrorOutput("Dependencies can not be declared against the `foo` configuration.")
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
        failure.hasErrorOutput("Dependency constraints can not be declared against the `compile` configuration.")
    }

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/22339")
    def "fail if an artifact is added to an unusable configuration"() {
        given:
        buildFile << """
            artifacts {
                compile file('some.jar')
            }
        """

        when:
        fails 'help'

        then:
        failure.hasErrorOutput("Archives can not be added to the `compile` configuration.")
    }

    def "fail if a non-resolvable configuration is resolved"() {
        given:
        buildFile << """
            task resolve {
                dependsOn configurations.compileOnly
                doLast {
                    configurations.compileOnly.files
                }
            }
        """

        when:
        fails 'resolve'

        then:
        failure.hasErrorOutput("Resolving dependency configuration 'compileOnly' is not allowed as it is defined as 'canBeResolved=false'.")
        failure.hasErrorOutput("Instead, a resolvable ('canBeResolved=true') dependency configuration that extends 'compileOnly' should be resolved.")
    }
}
