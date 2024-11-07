/*
 * Copyright 2024 the original author or authors.
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
 * Verifies behavior of lazily-registered configurations when performing dependency resolution.
 *
 * @see org.gradle.api.artifacts.ConfigurationContainer#register(String)
 * @see org.gradle.api.artifacts.ConfigurationContainer#consumable(String)
 * @see org.gradle.api.artifacts.ConfigurationContainer#resolvable(String)
 * @see org.gradle.api.artifacts.ConfigurationContainer#dependencyScope(String)
 */
class LazyConfigurationResolveIntegrationTest extends AbstractIntegrationSpec {

    def "does not realize non-consumable, unrelated, role-locked configurations in target project"() {
        settingsFile << "include('producer')"

        file("producer/build.gradle") << """
            configurations.configureEach {
                println("Realizing configuration \$name")
            }

            configurations {
                resolvable("unrelatedResolvable") {
                    assert false
                }
                dependencyScope("unrelatedDependencyScope") {
                    assert false
                }

                // TODO: A lazy extendsFrom mechanism would allow us to avoid realizing otherDependencies
                dependencyScope("otherDependencies")
                consumable("otherConsumable") {
                    extendsFrom(otherDependencies)
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "other"))
                }

                dependencyScope("mainDependencies")
                consumable("main") {
                    extendsFrom(mainDependencies)
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "main"))
                    outgoing.artifact(file("main.txt"))
                }
            }
        """

        buildFile << """
            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "main"))
                }
            }

            dependencies {
                deps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.named("res").map { it.incoming.files }
                doLast {
                    assert files.get()*.name == ["main.txt"]
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputDoesNotContain("Realizing configuration")

        when:
        succeeds(":resolve")

        then:
        outputContains("""
Realizing configuration otherConsumable
Realizing configuration otherDependencies
Realizing configuration main
Realizing configuration mainDependencies
        """)
    }

    def "realizes non-role-locked configurations in target project"() {
        settingsFile << "include('producer')"

        file("producer/build.gradle") << """
            configurations.configureEach {
                println("Realizing configuration \$name")
            }

            configurations {
                register("unrelated")

                consumable("main") {
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "main"))
                    outgoing.artifact(file("main.txt"))
                }
            }
        """

        buildFile << """
            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "main"))
                }
            }

            dependencies {
                deps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.named("res").map { it.incoming.files }
                doLast {
                    assert files.get()*.name == ["main.txt"]
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputDoesNotContain("Realizing configuration")

        when:
        succeeds(":resolve")

        then:
        outputContains("""
Realizing configuration main
Realizing configuration unrelated
        """)
    }

    def "can consume lazy legacy configuration from target project"() {
        settingsFile << "include('producer')"

        file("producer/build.gradle") << """
            configurations {
                register("main") {
                    canBeResolved = false
                    canBeDeclared = false

                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "main"))
                    outgoing.artifact(file("main.txt"))
                }
            }
        """

        buildFile << """
            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "main"))
                }
            }

            dependencies {
                deps(project(":producer"))
            }

            tasks.register("resolve") {
                def files = configurations.named("res").map { it.incoming.files }
                doLast {
                    assert files.get()*.name == ["main.txt"]
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputDoesNotContain("Realizing configuration")

        expect:
        succeeds(":resolve")
    }

    def "unrelated lazy configurations in current project are not realized when resolving configuration"() {

        mavenRepo.module("org", "foo").publish()

        buildFile << """
            configurations.configureEach {
                println("Realizing configuration \$name")
            }

            configurations {
                dependencyScope("unrelatedDependencies") {
                    assert false
                }
                consumable("unrelatedConsumable") {
                    assert false
                }
                resolvable("unrelatedResolvable") {
                    assert false
                }
                register("unrelatedLegacy") {
                    assert false
                }

                dependencyScope("deps") {
                    // Add the dependency lazily without realizing the configuration
                    dependencies.add(project.dependencies.create("org:foo:1.0"))
                }
                resolvable("res") {
                    extendsFrom(deps)
                }
            }

            ${mavenTestRepository()}

            tasks.register("resolve") {
                def files = configurations.named("res").map { it.incoming.files }
                doLast {
                    assert files.get()*.name == ["foo-1.0.jar"]
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputDoesNotContain("Realizing configuration")

        when:
        succeeds(":resolve")

        then:
        outputContains("""
Realizing configuration res
Realizing configuration deps
        """)
    }
}
