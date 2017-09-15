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

    void "optional dependency is not integrated into the result of resolution"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:1.0') {
                   optional = true
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == []
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "optional dependency is included into the result of resolution when a hard dependency is also added"() {
        given:
        mavenRepo.module("org", "foo", '1.0').publish()
        mavenRepo.module("org", "foo", '1.1').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:1.1') {
                   optional = true
                }
                conf 'org:foo:1.0'
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

    void "optional dependency is included into the result of resolution when a hard dependency is also added transitively"() {
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
                conf('org:foo:1.1') {
                   optional = true
                }
                conf 'org:bar:1.0'
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

    void "range resolution kicks in with optional dependencies"() {
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
                conf('org:foo:[1.0,1.1]') {
                   optional = true
                }
                conf 'org:bar:1.0'
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

    void "transitive dependencies of an optional dependency are not included in the graph"() {
        given:
        mavenRepo.module("org", "foo", '1.0').dependsOn('org', 'bar', '1.0').publish()
        mavenRepo.module("org", "bar", '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf('org:foo:1.0') {
                   optional = true
                }
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == []
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    void "transitive dependencies of an optional dependency do not participate in conflict resolution"() {
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
                conf('org:foo:1.0') {
                   optional = true
                }
                conf 'org:bar:1.0'
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

    void "optionality of Maven modules is respected"() {
        given:
        def bar = mavenRepo.module('org', 'bar', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.0').dependsOn(bar, optional: true).publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:foo:1.0'
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

    void "optional Maven dependency is included if hard dependency is added too"() {
        given:
        def bar10 = mavenRepo.module('org', 'bar', '1.0').publish()
        def bar11 = mavenRepo.module('org', 'bar', '1.1').publish()
        mavenRepo.module('org', 'foo', '1.0').dependsOn(bar11, optional: true).publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:foo:1.0'
                conf 'org:bar:1.0'
            }
            task checkDeps {
                doLast {
                    def files = configurations.conf*.name.sort()
                    assert files == ['bar-1.1.jar', 'foo-1.0.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

}
