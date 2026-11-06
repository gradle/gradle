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
 * Integration tests for {@link org.gradle.api.plugins.JavaPlatformBuilderPlugin}, covering
 * platforms that are built by one project and consumed by another project in the same build.
 */
class JavaPlatformBuilderLocalResolveIntegrationTest extends AbstractIntegrationSpec {

    ResolveTestFixture resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        settingsFile << """
            rootProject.name = "test"
            include("platform")

            dependencyResolutionManagement {
                ${mavenTestRepository()}
            }
        """
    }

    def "consuming project gets versions from the graph resolved by the platform"() {
        given:
        def baz = mavenRepo.module("org", "baz", "1.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn(baz).publish()

        platformProject("""
            platformApi("org:foo:1.0")
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api("org:foo")
            api("org:baz")
        """)

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":platform", "test:platform:unspecified") {
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

    def "conflicts in the platform graph are resolved while building the platform"() {
        given:
        def baz10 = mavenRepo.module("org", "baz", "1.0").publish()
        def baz20 = mavenRepo.module("org", "baz", "2.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn(baz10).publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn(baz20).publish()

        platformProject("""
            platformApi("org:foo:1.0")
            platformApi("org:bar:1.0")
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api("org:foo")
            api("org:bar")
            api("org:baz")
        """)

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":platform", "test:platform:unspecified") {
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

    def "platform graph containing a project dependency produces a constraint on that project"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()

        settingsFile << """
            include("lib")
        """
        file("lib/build.gradle") << """
            plugins {
                id("java-library")
            }

            version = "1.0"

            dependencies {
                api("org:foo:1.0")
            }
        """

        platformProject("""
            platformApi(project(":lib"))
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api(project(":lib"))
            api("org:foo")
        """, "runtimeClasspath")

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":platform", "test:platform:unspecified") {
                    constraint("project ':lib'", ":lib", "test:lib:1.0")
                    constraint("org:foo:1.0")
                    noArtifacts()
                }
                project(":lib", "test:lib:1.0") {
                    byConstraint()
                    module("org:foo:1.0")
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                }
            }
        }
    }

    def "apiElements variant is constrained by the api graph only"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "2.0").publish()

        platformProject("""
            platformApi("org:foo:1.0")
            platformRuntime("org:bar:2.0")
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api("org:foo")
            api("org:bar:1.0")
        """)

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":platform", "test:platform:unspecified") {
                    constraint("org:foo:1.0")
                    noArtifacts()
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                }
                module("org:bar:1.0")
            }
        }
    }

    def "runtimeElements variant is constrained by both the api and runtime graphs"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()

        platformProject("""
            platformApi("org:foo:1.0")
            platformRuntime("org:bar:1.0")
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api("org:foo")
            api("org:bar")
        """, "runtimeClasspath")

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":platform", "test:platform:unspecified") {
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

    def "api consumers cannot get versions that are only declared in the runtime graph"() {
        given:
        mavenRepo.module("org", "bar", "1.0").publish()

        platformProject("""
            platformRuntime("org:bar:1.0")
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api("org:bar")
        """)

        when:
        fails(":checkDeps")

        then:
        failure.assertHasCause("Could not find org:bar:.")
    }

    def "runtimeElements variant aligns with versions in apiElements variant"() {
        given:
        def baz10 = mavenRepo.module("org", "baz", "1.0").publish()
        def baz20 = mavenRepo.module("org", "baz", "2.0").publish()
        mavenRepo.module("org", "foo", "1.0").dependsOn(baz10).publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn(baz20).publish()

        platformProject("""
            platformApi("org:foo:1.0")
            platformRuntime("org:bar:1.0")
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api("org:foo")
        """, "runtimeClasspath")

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":platform", "test:platform:unspecified") {
                    constraint("org:foo:1.0")
                    constraint("org:baz:1.0")
                    noArtifacts()
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                    module("org:baz:1.0") {
                        byConstraint()
                    }
                }
            }
        }
    }

    def "constraints declared directly on the platform are combined with the generated constraints"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "baz", "1.0").publish()

        platformProject("""
            platformApi("org:foo:1.0")

            constraints {
                api("org:bar:1.0")
                runtime("org:baz:1.0")
            }
        """)

        consumerProject("""
            api(platform(project(":platform")))
            api("org:foo")
            api("org:bar")
            api("org:baz")
        """, "runtimeClasspath")

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":platform", "test:platform:unspecified") {
                    constraint("org:foo:1.0")
                    constraint("org:bar:1.0")
                    constraint("org:baz:1.0")
                    noArtifacts()
                }
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                }
                edge("org:bar", "org:bar:1.0") {
                    byConstraint()
                }
                edge("org:baz", "org:baz:1.0") {
                    byConstraint()
                }
            }
        }
    }

    def "building the platform fails when the platform graph cannot be resolved"() {
        given:
        platformProject("""
            platformApi("org:does-not-exist:1.0")
        """)

        consumerProject("""
            api(platform(project(":platform")))
        """)

        when:
        fails(":checkDeps")

        then:
        failure.assertHasCause("Could not find org:does-not-exist:1.0.")
    }

    private void platformProject(String dependencies) {
        file("platform/build.gradle") << """
            plugins {
                id("java-platform-builder")
            }

            dependencies {
                $dependencies
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
