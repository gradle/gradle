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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.dsl.GradleDsl
import spock.lang.Issue

/**
 * Tests for {@code DependencySubstitutions.withModule} and {@code DependencySubstitutions.withProject},
 * which change the target of a substitution while keeping the attributes and capabilities of the request.
 */
class DependencySubstitutionKeepingVariantIntegrationTest extends AbstractIntegrationSpec {

    def resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        settingsFile << """
            rootProject.name = "depsub"
        """
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            ${resolve.configureProject("runtimeClasspath")}
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/14396")
    def "can rewrite the group of every dependency without breaking platform or test fixtures dependencies"() {
        def external = mavenRepo.module("org", "external", "2.0").publish()
        mavenRepo.module("com.mycompany.branch", "platform", "1.0-branch").hasPackaging("pom").dependencyConstraint(external).publish()
        publishLibraryWithTestFixtures("com.mycompany.branch", "lib", "1.0-branch")
        mavenRepo.module("com.mycompany.branch", "service", "1.0-branch").dependsOn("com.mycompany", "lib", "1.0").publish()

        buildFile << """
            dependencies {
                implementation(platform("com.mycompany:platform:1.0"))
                implementation("org:external")
                implementation("com.mycompany:service:1.0")
                implementation(testFixtures("com.mycompany:lib:1.0"))
            }

            def branchGroups = ["com.mycompany": "com.mycompany.branch"]
            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ModuleComponentSelector && branchGroups.containsKey(requested.group)) {
                        useTarget(withModule(requested, branchGroups[requested.group], requested.module, "1.0-branch"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("com.mycompany:platform:1.0", "com.mycompany.branch:platform:1.0-branch") {
                    variant("platform-runtime")
                    selectedByRule()
                    noArtifacts()
                    constraint("org:external:2.0")
                }
                edge("org:external", "org:external:2.0") {
                    byConstraint()
                }
                edge("com.mycompany:service:1.0", "com.mycompany.branch:service:1.0-branch") {
                    selectedByRule()
                    edge("com.mycompany:lib:1.0", "com.mycompany.branch:lib:1.0-branch") {
                        variant("runtime")
                        selectedByRule()
                    }
                }
                edge("com.mycompany:lib:1.0", "com.mycompany.branch:lib:1.0-branch") {
                    variant("testFixturesRuntime")
                    artifact(classifier: "test-fixtures")
                }
            }
        }
    }

    def "withModule keeps a platform dependency a platform"() {
        mavenRepo.module(group, "bom", "2.0").hasPackaging("pom").publish()

        buildFile << """
            dependencies {
                implementation(platform("org:bom:1.0"))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ModuleComponentSelector && requested.module == "bom") {
                        useTarget(withModule(requested, "${group}", "bom", "2.0"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org:bom:1.0", "${group}:bom:2.0") {
                    variant("platform-runtime")
                    selectedByRule()
                    noArtifacts()
                }
            }
        }

        where:
        group << ["org", "other"]
    }

    def "withModule keeps the requested test fixtures capability"() {
        publishLibraryWithTestFixtures(group, "lib", "2.0")

        buildFile << """
            dependencies {
                implementation(testFixtures("org:lib:1.0"))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ModuleComponentSelector && requested.module == "lib") {
                        useTarget(withModule(requested, "${group}", "lib", "2.0"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org:lib:1.0", "${group}:lib:2.0") {
                    variant("testFixturesRuntime")
                    selectedByRule()
                    artifact(classifier: "test-fixtures")
                }
            }
        }

        where:
        group << ["org", "other"]
    }

    def "withModule replaces a project dependency with a module and keeps the requested test fixtures capability"() {
        publishLibraryWithTestFixtures("org", "lib", "2.0")

        settingsFile << """
            include("lib")
        """
        file("lib/build.gradle") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }
        """

        buildFile << """
            dependencies {
                implementation(testFixtures(project(":lib")))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ProjectComponentSelector) {
                        useTarget(withModule(requested, "org", "lib", "2.0"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("project ':lib'", "org:lib:2.0") {
                    variant("testFixturesRuntime")
                    selectedByRule()
                    artifact(classifier: "test-fixtures")
                }
            }
        }
    }

    def "withModule replaces a project platform dependency with a module and keeps it a platform"() {
        mavenRepo.module("org", "bom", "2.0").hasPackaging("pom").publish()

        settingsFile << """
            include("platform")
        """
        file("platform/build.gradle") << """
            plugins {
                id("java-platform")
            }
        """

        buildFile << """
            dependencies {
                implementation(platform(project(":platform")))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ProjectComponentSelector) {
                        useTarget(withModule(requested, "org", "bom", "2.0"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("project ':platform'", "org:bom:2.0") {
                    variant("platform-runtime")
                    selectedByRule()
                    noArtifacts()
                }
            }
        }
    }

    def "withModule interprets the version as in dependency notation: #version"() {
        ["1.0", "2.0", "2.5", "3.0"].each {
            mavenRepo.module("org", "lib", it).publish()
        }
        mavenRepo.module("org", "consumer", "1.0").dependsOn("org", "lib", "3.0").publish()

        buildFile << """
            dependencies {
                implementation("org:lib:1.0")
                implementation("org:consumer:1.0")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ModuleComponentSelector && requested.module == "lib" && requested.version == "1.0") {
                        useTarget(withModule(requested, "org", "lib", "${version}"))
                    }
                }
            }
        """

        when:
        succeeds(":dependencies", "--configuration", "runtimeClasspath")

        then:
        outputContains("org:lib:1.0 -> ${selected}")

        where:
        version            | selected
        "2.0"              | "3.0"
        "2.0!!"            | "2.0"
        "[2.0, 3.0)!!"     | "2.5"
        "[2.0, 3.0)!!2.0"  | "2.0"
    }

    def "withModule accepts a rich version"() {
        ["1.0", "2.0", "3.0"].each {
            mavenRepo.module("org", "lib", it).publish()
        }
        mavenRepo.module("org", "consumer", "1.0").dependsOn("org", "lib", "3.0").publish()

        buildFile << """
            dependencies {
                implementation("org:lib:1.0")
                implementation("org:consumer:1.0")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ModuleComponentSelector && requested.module == "lib" && requested.version == "1.0") {
                        useTarget(withModule(requested, "org", "lib") {
                            strictly("[2.0, 3.0)")
                            prefer("2.0")
                        })
                    }
                }
            }
        """

        when:
        succeeds(":dependencies", "--configuration", "runtimeClasspath")

        then:
        outputContains("org:lib:1.0 -> 2.0")
    }

    def "withModule accepts a rich version in the Kotlin DSL"() {
        ["1.0", "2.0", "3.0"].each {
            mavenRepo.module("org", "lib", it).publish()
        }
        mavenRepo.module("org", "consumer", "1.0").dependsOn("org", "lib", "3.0").publish()

        buildFile.delete()
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository(GradleDsl.KOTLIN)}

            dependencies {
                implementation("org:lib:1.0")
                implementation("org:consumer:1.0")
            }

            configurations.runtimeClasspath {
                resolutionStrategy.dependencySubstitution {
                    all {
                        val module = requested
                        if (module is ModuleComponentSelector && module.module == "lib" && module.version == "1.0") {
                            useTarget(withModule(module, "org", "lib") {
                                strictly("2.0")
                            })
                        }
                    }
                }
            }
        """

        when:
        succeeds(":dependencies", "--configuration", "runtimeClasspath")

        then:
        outputContains("org:lib:1.0 -> 2.0")
    }

    def "withProject replaces a platform dependency with a project and keeps it a platform"() {
        settingsFile << """
            include("platform")
        """
        file("platform/build.gradle") << """
            plugins {
                id("java-platform")
            }
        """

        buildFile << """
            dependencies {
                implementation(platform("org:bom:1.0"))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ModuleComponentSelector && requested.module == "bom") {
                        useTarget(withProject(requested, ":platform"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org:bom:1.0", ":platform", "depsub:platform:") {
                    variant("runtimeElements", ["org.gradle.category": "platform", "org.gradle.usage": "java-runtime"])
                    selectedByRule()
                    noArtifacts()
                }
            }
        }
    }

    def "withProject replaces a module dependency with a project and keeps the requested test fixtures capability"() {
        settingsFile << """
            include("lib")
        """
        file("lib/build.gradle") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }
        """

        buildFile << """
            dependencies {
                implementation(testFixtures("org:lib:1.0"))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ModuleComponentSelector && requested.module == "lib") {
                        useTarget(withProject(requested, ":lib"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org:lib:1.0", ":lib", "depsub:lib:") {
                    variant("testFixturesRuntimeElements")
                    selectedByRule()
                    artifact(classifier: "test-fixtures")
                    project(":lib", "depsub:lib:") {
                        variant("runtimeElements")
                        artifact(name: "lib")
                    }
                }
            }
        }
    }

    def "withProject replaces a project platform dependency with another project and keeps it a platform"() {
        settingsFile << """
            include("old")
            include("new")
        """
        file("old/build.gradle") << """
            plugins {
                id("java-platform")
            }
        """
        file("new/build.gradle") << """
            plugins {
                id("java-platform")
            }
        """

        buildFile << """
            dependencies {
                implementation(platform(project(":old")))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ProjectComponentSelector && requested.projectPath == ":old") {
                        useTarget(withProject(requested, ":new"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("project ':old'", ":new", "depsub:new:") {
                    variant("runtimeElements", ["org.gradle.category": "platform", "org.gradle.usage": "java-runtime"])
                    selectedByRule()
                    noArtifacts()
                }
            }
        }
    }

    def "withProject replaces a project dependency with another project and keeps the requested test fixtures capability"() {
        settingsFile << """
            include("old")
            include("new")
        """
        file("old/build.gradle") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }
        """
        file("new/build.gradle") << """
            plugins {
                id("java-library")
                id("java-test-fixtures")
            }
        """

        buildFile << """
            dependencies {
                implementation(testFixtures(project(":old")))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                all {
                    if (requested instanceof ProjectComponentSelector && requested.projectPath == ":old") {
                        useTarget(withProject(requested, ":new"))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("project ':old'", ":new", "depsub:new:") {
                    variant("testFixturesRuntimeElements")
                    selectedByRule()
                    artifact(classifier: "test-fixtures")
                    project(":new", "depsub:new:") {
                        variant("runtimeElements")
                        artifact(name: "new")
                    }
                }
            }
        }
    }

    private void publishLibraryWithTestFixtures(String group, String name, String version) {
        Map<String, String> runtime = ["org.gradle.usage": "java-runtime", "org.gradle.category": "library"]
        mavenRepo.module(group, name, version)
            .withModuleMetadata()
            .withoutDefaultVariants()
            .variant("runtime", runtime) {
                artifact("${name}-${version}.jar")
            }
            .variant("testFixturesRuntime", runtime) {
                capability(group, "${name}-test-fixtures", version)
                artifact("${name}-${version}-test-fixtures.jar")
            }
            .publish()
    }

}
