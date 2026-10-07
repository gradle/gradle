/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

/**
 * Integration tests for {@link JavaPlatformBuilderPlugin}, covering
 * platforms that are published to a repository and consumed from a separate build.
 */
class JavaPlatformBuilderExternalResolveIntegrationTest extends AbstractIntegrationSpec {

    ResolveTestFixture resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        settingsFile << """
            rootProject.name = "test"

            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """
    }

    def "consuming build gets versions from the published platform"() {
        given:
        def baz = mavenRepo.module("org", "baz", "1.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn(baz).publish()

        publishPlatform("""
            platformApi("org:foo:1.0")
        """)

        consumerProject("""
            api(platform("org.test:platform:1.0"))
            api("org:foo")
            api("org:baz")
        """)

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.test:platform:1.0") {
                    constraint("org:foo:1.0")
                    constraint("org:baz:1.0")
                    noArtifacts()
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                    module("org:baz:1.0")
                }
                edge("org:baz", "org:baz:1.0") {
                    byConstraint()
                }
            }
        }
    }

    def "conflicts in the platform graph are resolved before the platform is published"() {
        given:
        def baz10 = mavenRepo.module("org", "baz", "1.0").publish()
        def baz20 = mavenRepo.module("org", "baz", "2.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn(baz10).publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn(baz20).publish()

        publishPlatform("""
            platformApi("org:foo:1.0")
            platformApi("org:bar:1.0")
        """)

        expect:
        def platform = mavenRepo.module("org.test", "platform", "1.0")
        platform.parsedModuleMetadata.variant("apiElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:bar:1.0", "org:baz:2.0")
            noMoreDependencies()
        }
        platform.parsedModuleMetadata.variant("runtimeElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:bar:1.0", "org:baz:2.0")
            noMoreDependencies()
        }
        platform.parsedPom.scope("no_scope") {
            assertNoDependencies()
            assertDependencyManagementInOrder("org:foo:1.0", "org:bar:1.0", "org:baz:2.0")
        }

        when:
        consumerProject("""
            api(platform("org.test:platform:1.0"))
            api("org:foo")
            api("org:bar")
            api("org:baz")
        """)

        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.test:platform:1.0") {
                    constraint("org:foo:1.0")
                    constraint("org:bar:1.0")
                    constraint("org:baz:2.0")
                    noArtifacts()
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                    edge("org:baz:1.0", "org:baz:2.0") {
                        byConstraint()
                        byConflictResolution("between versions 2.0 and 1.0")
                    }
                }
                edge("org:bar", "org:bar:1.0") {
                    byConstraint()
                    module("org:baz:2.0")
                }
                edge("org:baz", "org:baz:2.0")
            }
        }
    }

    def "api and runtime graphs are published to their matching variants"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        publishPlatform("""
            platformApi("org:foo:1.0")
            platformRuntime("org:bar:1.0")
        """)

        expect:
        def platform = mavenRepo.module("org.test", "platform", "1.0")
        platform.parsedModuleMetadata.variant("apiElements") {
            assertHasConstraintsInOrder("org:foo:1.0")
            noMoreDependencies()
        }
        platform.parsedModuleMetadata.variant("runtimeElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:bar:1.0")
            noMoreDependencies()
        }
        platform.parsedPom.scope("no_scope") {
            assertNoDependencies()
            assertDependencyManagementInOrder("org:foo:1.0", "org:bar:1.0")
        }

        when:
        consumerProject("""
            api(platform("org.test:platform:1.0"))
            api("org:foo")
            api("org:bar")
        """, "runtimeClasspath")

        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.test:platform:1.0") {
                    constraint("org:foo:1.0")
                    constraint("org:bar:1.0")
                    noArtifacts()
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                }
                edge("org:bar", "org:bar:1.0") {
                    byConstraint()
                }
            }
        }
    }

    def "the runtime view of an api dependency is published to the runtime variant"() {
        given:
        def compileDep = mavenRepo.module("org", "compile-dep", "1.0").publish()
        def runtimeDep = mavenRepo.module("org", "runtime-dep", "1.0").publish()
        mavenRepo.module("org", "foo", "1.0")
            .dependsOn(compileDep)
            .dependsOn(runtimeDep, scope: "runtime")
            .publish()

        publishPlatform("""
            platformApi("org:foo:1.0")
        """)

        expect:
        def platform = mavenRepo.module("org.test", "platform", "1.0")
        platform.parsedModuleMetadata.variant("apiElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:compile-dep:1.0")
            noMoreDependencies()
        }
        platform.parsedModuleMetadata.variant("runtimeElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:compile-dep:1.0", "org:runtime-dep:1.0")
            noMoreDependencies()
        }
        platform.parsedPom.scope("no_scope") {
            assertNoDependencies()
            assertDependencyManagementInOrder("org:foo:1.0", "org:compile-dep:1.0", "org:runtime-dep:1.0")
        }
    }

    def "platform graph containing a project dependency is published as a constraint on that project's coordinates"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        producerLibProject()

        publishPlatform("""
            platformApi(project(":lib"))
        """)

        expect:
        def platform = mavenRepo.module("org.test", "platform", "1.0")
        platform.parsedModuleMetadata.variant("apiElements") {
            assertHasConstraintsInOrder("org.test:lib:1.0", "org:foo:1.0")
            noMoreDependencies()
        }
        platform.parsedModuleMetadata.variant("runtimeElements") {
            assertHasConstraintsInOrder("org.test:lib:1.0", "org:foo:1.0")
            noMoreDependencies()
        }
        platform.parsedPom.scope("no_scope") {
            assertNoDependencies()
            assertDependencyManagementInOrder("org.test:lib:1.0", "org:foo:1.0")
        }

        when:
        consumerProject("""
            api(platform("org.test:platform:1.0"))
            api("org.test:lib")
            api("org:foo")
        """)

        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.test:platform:1.0") {
                    constraint("org.test:lib:1.0")
                    constraint("org:foo:1.0")
                    noArtifacts()
                }
                edge("org.test:lib", "org.test:lib:1.0") {
                    byConstraint()
                    module("org:foo:1.0")
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                }
            }
        }
    }

    def "runtimeElements variant aligns with versions in apiElements variant"() {
        given:
        def baz10 = mavenRepo.module("org", "baz", "1.0").publish()
        def baz20 = mavenRepo.module("org", "baz", "2.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn(baz10).publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn(baz20).publish()

        publishPlatform("""
            platformApi("org:foo:1.0")
            platformRuntime("org:bar:1.0")
        """)

        expect:
        def platform = mavenRepo.module("org.test", "platform", "1.0")
        platform.parsedModuleMetadata.variant("apiElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:baz:1.0")
            noMoreDependencies()
        }
        platform.parsedModuleMetadata.variant("runtimeElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:bar:1.0", "org:baz:1.0")
            noMoreDependencies()
        }
        platform.parsedPom.scope("no_scope") {
            assertNoDependencies()
            // Currently, POM and GMM list constraints in different orders. This is not desired behavior.
            assertDependencyManagementInOrder("org:foo:1.0", "org:baz:1.0", "org:bar:1.0")
        }
    }

    def "constraints declared directly on the platform are published alongside the generated constraints"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "baz", "1.0").publish()

        publishPlatform("""
            platformApi("org:foo:1.0")

            constraints {
                api("org:bar:1.0")
                runtime("org:baz:1.0")
            }
        """)

        expect:
        def platform = mavenRepo.module("org.test", "platform", "1.0")
        platform.parsedModuleMetadata.variant("apiElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:bar:1.0")
            noMoreDependencies()
        }
        platform.parsedModuleMetadata.variant("runtimeElements") {
            assertHasConstraintsInOrder("org:foo:1.0", "org:baz:1.0", "org:bar:1.0")
            noMoreDependencies()
        }
        platform.parsedPom.scope("no_scope") {
            // Currently, POM and GMM list constraints in different orders. This is not desired behavior.
            assertDependencyManagementInOrder("org:foo:1.0", "org:bar:1.0", "org:baz:1.0")
        }
    }

    def "publishing the platform fails when the platform graph cannot be resolved"() {
        given:
        producerBuild("""
            platformApi("org:does-not-exist:1.0")
        """)

        when:
        def publishFailure = executer.inDirectory(file("producer")).withTasks("publish").runWithFailure()

        then:
        publishFailure.assertHasCause("Could not find org:does-not-exist:1.0.")
    }

    /**
     * Builds and publishes a platform from a separate build in the {@code producer} directory.
     */
    private void publishPlatform(String dependencies) {
        producerBuild(dependencies)
        executer.inDirectory(file("producer")).withTasks("publish").run()
    }

    private void producerBuild(String dependencies) {
        file("producer/settings.gradle") << """
            rootProject.name = "platform"
        """
        file("producer/build.gradle") << """
            plugins {
                id("java-platform-builder")
                id("maven-publish")
            }

            group = "org.test"
            version = "1.0"

            ${mavenTestRepository()}

            dependencies {
                $dependencies
            }

            publishing {
                ${mavenTestRepository()}
                publications {
                    maven(MavenPublication) {
                        from(components.javaPlatform)
                    }
                }
            }
        """
    }

    /**
     * Adds a published project to the producer build for the platform to depend on.
     */
    private void producerLibProject() {
        file("producer/settings.gradle") << """
            include("lib")
        """
        file("producer/lib/build.gradle") << """
            plugins {
                id("java-library")
                id("maven-publish")
            }

            group = "org.test"
            version = "1.0"

            ${mavenTestRepository()}

            dependencies {
                api("org:foo:1.0")
            }

            publishing {
                ${mavenTestRepository()}
                publications {
                    maven(MavenPublication) {
                        from(components.java)
                    }
                }
            }
        """
    }

    private void consumerProject(String dependencies, String configuration = "compileClasspath") {
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                $dependencies
            }

            ${resolve.configureProject(configuration)}
        """
    }

}
