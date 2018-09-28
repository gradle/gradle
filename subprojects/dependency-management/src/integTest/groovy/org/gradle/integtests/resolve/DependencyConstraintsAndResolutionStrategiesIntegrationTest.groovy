/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

/**
 * These test cases document the current behavior when dependency constraints are combined
 * with other dependency management mechanisms. They do not represent recommended use cases.
 * If dependency constraints and component metadata rules are used, using other mechanisms
 * should not be required.
 */
class DependencyConstraintsAndResolutionStrategiesIntegrationTest extends AbstractIntegrationSpec {
    private final ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf").expectDefaultConfiguration("runtime")

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
        """
        def foo11 = mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "bar", '1.0').dependsOn(foo11).publish()
    }

    void "force resolution strategy is applied to dependency constraints"() {
        given:
        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
            configurations.conf.resolutionStrategy {
                force 'org:foo:1.0'
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edgeFromConstraint("org:foo:1.1","org:foo:1.0")
                module("org:bar:1.0") {
                    edge("org:foo:1.0","org:foo:1.0")
                }
            }
        }
    }

    void "fail-on-conflict resolution strategy is applied to dependency constraints"() {
        given:
        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
            configurations.conf.resolutionStrategy {
                failOnVersionConflict()
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause """A conflict was found between the following modules:
  - org:foo:1.0
  - org:foo:1.1"""
    }

    void "dependency substitution rules are applied to dependency constraints"() {
        given:
        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
            configurations.conf.resolutionStrategy {
                dependencySubstitution {
                    substitute module("org:foo:1.1") with module("org:foo:1.0")
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edgeFromConstraint("org:foo:1.1","org:foo:1.0")
                module("org:bar:1.0") {
                    edge("org:foo:1.0","org:foo:1.0")
                }
            }
        }
    }

    void "dependency resolve rules are applied to dependency constraints"() {
        given:
        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
            configurations.conf.resolutionStrategy {
                eachDependency { DependencyResolveDetails details ->
                    if (details.requested.group == 'org') {
                        details.useVersion '1.0'
                    }
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edgeFromConstraint("org:foo:1.1","org:foo:1.0")
                module("org:bar:1.0") {
                    edge("org:foo:1.0","org:foo:1.0")
                }
            }
        }
    }

    void "module replacement rules are applied to dependency constraints"() {
        given:
        buildFile << """
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:baz:1.1' //constraint ignored due to replacement rule
                }
                modules {
                    module("org:baz") {
                        replacedBy("org:foo")
                    }
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:bar:1.0") {
                    edge("org:foo:1.0","org:foo:1.0")
                }
            }
        }
    }
}
