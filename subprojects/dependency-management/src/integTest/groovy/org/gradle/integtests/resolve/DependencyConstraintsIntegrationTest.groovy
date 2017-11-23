
/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.integtests.fixtures.ExperimentalFeaturesFixture

/**
 * This is a variation of {@link OptionalDependenciesIntegrationTest} that tests dependency constraints
 * declared in the build script (instead of published optional dependencies)
 */
class DependencyConstraintsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        ExperimentalFeaturesFixture.enable(settingsFile)
        settingsFile << "rootProject.name = 'test'"
    }

    void "dependency constraint is ignored when feature not enabled"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()

        // Do not enable feature
        settingsFile.text = """
            rootProject.name = 'test'
"""
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:foo:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['foo-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "dependency constraint is included into the result of resolution when a hard dependency is also added"() {
        given:
        mavenRepo.module("org", "foo", '1.1').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:foo'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['foo-1.1.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "dependency constraint is included into the result of resolution when a hard dependency is also added transitively"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn("org", "foo", "1.0").publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.1'
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.1.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "range resolution kicks in with dependency constraints"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.2').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn("org", "foo", "[1.0,1.2]").publish()


        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:[1.0,1.1]'
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.1.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "transitive dependencies of an dependency constraint do not participate in conflict resolution if it is not included elsewhere"() {
        given:
        mavenRepo.module("org", "foo", '1.0').dependsOn('org', 'bar', '1.1').publish()
        mavenRepo.module("org", "bar", '1.0').publish()
        mavenRepo.module("org", "bar", '1.1').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:bar:1.0'
                constraints {
                    conf 'org:foo:1.0'
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "dependency constraints on substituted module is recognized properly"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "bar", '1.1').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                   resolutionStrategy.dependencySubstitution {
                      all { DependencySubstitution dependency ->
                         if (dependency.requested.module == 'bar') {
                            dependency.useTarget dependency.requested.group + ':foo:' + dependency.requested.version
                         }
                      }
                   }
                }
            }
            dependencies {
                conf 'org:foo:1.0'
                constraints {
                    conf 'org:bar:1.1'
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['foo-1.1.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }
}
