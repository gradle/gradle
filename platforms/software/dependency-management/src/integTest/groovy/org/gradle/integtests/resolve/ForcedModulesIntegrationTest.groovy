/*
 * Copyright 2013 the original author or authors.
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

class ForcedModulesIntegrationTest extends AbstractIntegrationSpec {
    private ResolveTestFixture resolve = new ResolveTestFixture(buildFile)

    void "can force the version of a particular module"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        buildFile << """
            plugins {
                id("java-library")
            }
            ${mavenTestRepository()}

            dependencies {
                implementation 'org:foo:1.3.3'
            }

            configurations.all {
                resolutionStrategy.force 'org:foo:1.4.4'
            }

            task checkDeps {
                def compileClasspath = configurations.compileClasspath
                doLast {
                    assert compileClasspath*.name == ['foo-1.4.4.jar']
                }
            }
        """

        expect:
        succeeds("checkDeps")
    }

    void "can force the version of a transitive dependency module"() {
        mavenRepo.module("org", "foo", '1.3.3')
            .dependsOn("org", "bar", '1.1')
            .publish()
        mavenRepo.module("org", "bar", '1.0').publish()

        buildFile << """
            plugins {
                id("java-library")
            }
            ${mavenTestRepository()}

            dependencies {
                implementation 'org:foo:1.3.3'
            }

            configurations.all {
                resolutionStrategy.force 'org:bar:1.0'
            }

            task checkDeps {
                def compileClasspath = configurations.compileClasspath
                doLast {
                    assert compileClasspath*.name == ['foo-1.3.3.jar', 'bar-1.0.jar']
                }
            }
        """

        expect:
        succeeds("checkDeps")
    }

    void "can force already resolved version of a module and avoid conflict"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        settingsFile << """
            include 'api'
            include 'impl'
            include 'tool'
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """

        file("api/build.gradle") << """
            plugins {
                id("java-library")
            }
            configurations.all {
                resolutionStrategy {
                    force 'org:foo:1.3.3'
                    failOnVersionConflict()
                }
            }
            dependencies {
                implementation (group: 'org', name: 'foo', version:'1.4.4')
            }
        """

        file("impl/build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation (group: 'org', name: 'foo', version:'1.3.3')
            }
        """

        file("tool/build.gradle") << """
            plugins {
                id("java-library")
            }
            configurations.all {
                resolutionStrategy {
                    force 'org:foo:1.3.3'
                    failOnVersionConflict()
                }
            }
            dependencies {
                implementation project(':api')
                implementation project(':impl')
            }
        """

        expect:
        succeeds("api:dependencies", "tool:dependencies")
    }

    void "can force arbitrary version of a module and avoid conflict"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foobar", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()
        mavenRepo.module("org", "foo", '1.5.5').publish()

        settingsFile << """
            include 'api'
            include 'impl'
            include 'tool'
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """

        file("api/build.gradle") << """
            plugins {
                id("java-library")
            }

            group = 'org.foo.unittests'
            version = '1.0'

            dependencies {
                implementation (group: 'org', name: 'foo', version:'1.4.4')
            }
        """

        file('impl/build.gradle') << """
            plugins {
                id("java-library")
            }

            group = 'org.foo.unittests'
            version = '1.0'

            dependencies {
                implementation (group: 'org', name: 'foo', version:'1.3.3')
            }
        """

        file("tool/build.gradle") << """
            plugins {
                id("java-library")
            }

            group = 'org.foo.unittests'
            version = '1.0'

            dependencies {
                implementation project(':api')
                implementation project(':impl')
            }

            configurations.all {
                resolutionStrategy {
                    failOnVersionConflict()
                    force 'org:foo:1.5.5'
                }
            }
        """

        resolve.expectDefaultConfiguration("runtimeElements")
        resolve.prepare("runtimeClasspath")

        expect:
        succeeds(":tool:checkDeps")
        resolve.expectGraph {
            root(":tool", "org.foo.unittests:tool:1.0") {
                project(":api", "org.foo.unittests:api:1.0") {
                    edge("org:foo:1.4.4", "org:foo:1.5.5") {
                        forced()
                    }
                }
                project(":impl", "org.foo.unittests:impl:1.0") {
                    edge("org:foo:1.3.3", "org:foo:1.5.5")
                }
            }
        }
    }

    void "latest strategy respects forced modules"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        settingsFile << """
            include 'api'
            include 'impl'
            include 'tool'
            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """

        file("api/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation (group: 'org', name: 'foo', version:'1.3.3')
            }
        """

        file("impl/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation (group: 'org', name: 'foo', version:'1.4.4')
            }
        """

        file("tool/build.gradle") << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation project(':api')
                implementation project(':impl')
            }
            configurations.all {
                resolutionStrategy {
                    failOnVersionConflict()
                    force 'org:foo:1.3.3'
                }
            }
            task checkDeps {
                def runtimeClasspath = configurations.runtimeClasspath
                doLast {
                    assert runtimeClasspath*.name == ['api.jar', 'impl.jar', 'foo-1.3.3.jar']
                }
            }
        """

        expect:
        succeeds("tool:checkDeps")
    }

    void "forcing transitive dependency does not add extra dependency"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("hello", "world", '1.4.4').publish()

        buildFile << """
            plugins {
                id("java-library")
            }
            ${mavenTestRepository()}

            dependencies {
                implementation 'org:foo:1.3.3'
            }

            configurations.all {
                resolutionStrategy.force 'hello:world:1.4.4'
            }

            task checkDeps {
                def compileClasspath = configurations.compileClasspath
                doLast {
                    assert compileClasspath*.name == ['foo-1.3.3.jar']
                }
            }
        """

        expect:
        succeeds("checkDeps")
    }

    void "when forcing the same module last declaration wins"() {
        mavenRepo.module("org", "foo", '1.9').publish()

        buildFile << """
            plugins {
                id("java-library")
            }
            ${mavenTestRepository()}

            dependencies {
                implementation 'org:foo:1.0'
            }

            configurations.all {
                resolutionStrategy {
                    force 'org:foo:1.5'
                    force 'org:foo:2.0'
                    force 'org:foo:1.9'
                }
            }

            task checkDeps {
                def compileClasspath = configurations.compileClasspath
                doLast {
                    assert compileClasspath*.name == ['foo-1.9.jar']
                }
            }
        """

        expect:
        succeeds("checkDeps")
    }
}
