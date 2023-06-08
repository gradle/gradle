/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests component metadata rules for external components without metadata.
 */
class ComponentMetadataRulesMissingMetadataIntegrationTest extends AbstractIntegrationSpec {

    def "rule can define dependencies for component without metadata in Groovy DSL"() {
        given:
        mavenRepo.module("org", "foo").withNoPom().publish()
        mavenRepo.module("org", "bar").publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }

            dependencies {
                components {
                    withModule("org:foo") {
                        ["compile", "runtime"].each { base ->
                            withVariant(base) {
                                withDependencies {
                                    add("org:bar:1.0")
                                }
                            }
                        }
                    }
                }

                implementation "org:foo:1.0"
            }

            task verify {
                assert configurations.runtimeClasspath.files*.name == ["foo-1.0.jar", "bar-1.0.jar"]
            }
        """

        expect:
        succeeds "verify"
    }

    def "rule can define dependencies for component without metadata in Kotlin DSL"() {
        given:
        mavenRepo.module("org", "foo").withNoPom().publish()
        mavenRepo.module("org", "bar").publish()

        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven {
                    url = uri("${mavenRepo.uri}")
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }

            dependencies {
                components {
                    withModule("org:foo") {
                        listOf("compile", "runtime").forEach { base ->
                            withVariant(base) {
                                withDependencies {
                                    add("org:bar:1.0")
                                }
                            }
                        }
                    }
                }

                implementation("org:foo:1.0")
            }

            tasks.register("verify") {
                assert(configurations.runtimeClasspath.get().files.map { it.name } == listOf("foo-1.0.jar", "bar-1.0.jar"))
            }
        """

        expect:
        succeeds "verify"
    }

    def "rule can define dependency on component without metadata for component without metadata"() {
        given:
        mavenRepo.module("org", "foo").withNoPom().publish()
        mavenRepo.module("org", "bar").withNoPom().publish()
        mavenRepo.module("org", "baz").publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }

            dependencies {
                components {
                    withModule("org:foo") {
                        ["compile", "runtime"].each { base ->
                            withVariant(base) {
                                withDependencies {
                                    add("org:bar:1.0")
                                }
                            }
                        }
                    }
                    withModule("org:bar") {
                        ["compile", "runtime"].each { base ->
                            withVariant(base) {
                                withDependencies {
                                    add("org:baz:1.0")
                                }
                            }
                        }
                    }
                }

                implementation "org:foo:1.0"
            }

            task verify {
                assert configurations.runtimeClasspath.files*.name == ["foo-1.0.jar", "bar-1.0.jar", "baz-1.0.jar"]
            }
        """

        expect:
        succeeds "verify"
    }
}
