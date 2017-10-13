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

class OptionalDependenciesIntegrationTest extends AbstractIntegrationSpec {

    void "optional dependency is included into the result of resolution when a hard dependency is also added"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()

        mavenRepo.module("org", "root1", "1.0")
            .dependsOn(foo11, optional:true)
            .publish()
        mavenRepo.module("org", "root2", "1.0")
            .dependsOn(foo10)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:root1:1.0'
                conf 'org:root2:1.0'
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['foo-1.1.jar', 'root1-1.0.jar', 'root2-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "optional dependency is included into the result of resolution when a hard dependency is also added transitively"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn("org", "foo", "1.0").publish()

        mavenRepo.module("org", "root", "1.0")
            .dependsOn(foo11, optional:true)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:root:1.0')
                conf 'org:bar:1.0'
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.1.jar', 'root-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "range resolution kicks in with optional dependencies"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()
        mavenRepo.module("org", "foo", '1.2').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn("org", "foo", "[1.0,1.2]").publish()
        mavenRepo.module("org", "root", "1.0")
            .dependsOn(mavenRepo.module("org", "foo", '[1.0,1.1]'), optional:true)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:root:1.0')
                conf 'org:bar:1.0'
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'foo-1.1.jar', 'root-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "transitive dependencies of an optional dependency do not participate in conflict resolution if it is not included elsewhere"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').dependsOn('org', 'bar', '1.1').publish()
        mavenRepo.module("org", "bar", '1.0').publish()
        mavenRepo.module("org", "bar", '1.1').publish()

        mavenRepo.module("org", "root", "1.0")
            .dependsOn(foo10, optional:true)
            .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:root:1.0'
                conf 'org:bar:1.0'
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.0.jar', 'root-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "optional dependency on substituted module is recognized properly"() {
        given:
        def foo10 = mavenRepo.module("org", "foo", '1.0').publish()
        def foo11 = mavenRepo.module("org", "foo", '1.1').publish()
        def bar10 = mavenRepo.module("org", "bar", '1.0').publish()
        def bar11 = mavenRepo.module("org", "bar", '1.1').publish()
        mavenRepo.module("org", "root", "1.0")
            .dependsOn(bar11, optional:true)
            .dependsOn(foo10)
            .publish()

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
                conf 'org:root:1.0'
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['foo-1.1.jar', 'root-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

}
